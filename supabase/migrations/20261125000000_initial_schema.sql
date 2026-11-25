-- Initial schema for the sales app.
-- Business tables: customers, products, purchases, purchase_items, installments.
-- Bot tables (used only by the WhatsApp webhook with the service role):
-- authorized_senders, whatsapp_messages, bot_conversations.

-- ---------------------------------------------------------------------------
-- Helpers
-- ---------------------------------------------------------------------------

-- "Today" in the shop's time zone. The database runs in UTC, so plain
-- current_date would roll over at 21:00 in Brazil.
create or replace function public.local_today()
returns date
language sql
stable
set search_path = ''
as $$
  select (now() at time zone 'America/Sao_Paulo')::date;
$$;

create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create type public.payment_type as enum ('cash', 'installments');
create type public.purchase_source as enum ('app', 'whatsapp');

-- ---------------------------------------------------------------------------
-- Business tables
-- ---------------------------------------------------------------------------

create table public.customers (
  id uuid primary key default gen_random_uuid(),
  name text not null check (length(trim(name)) > 0),
  -- WhatsApp number in E.164 format, e.g. +5511999998888.
  phone text not null check (phone ~ '^\+[1-9][0-9]{7,14}$'),
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

-- A WhatsApp number identifies one active customer.
create unique index customers_phone_active_key
  on public.customers (phone) where deleted_at is null;
create index customers_name_idx on public.customers (lower(name));

create trigger customers_set_updated_at
  before update on public.customers
  for each row execute function public.set_updated_at();

create table public.products (
  id uuid primary key default gen_random_uuid(),
  name text not null check (length(trim(name)) > 0),
  price numeric(10, 2) not null check (price > 0),
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index products_name_active_key
  on public.products (lower(name)) where active;

create trigger products_set_updated_at
  before update on public.products
  for each row execute function public.set_updated_at();

create table public.purchases (
  id uuid primary key default gen_random_uuid(),
  customer_id uuid not null references public.customers (id),
  purchase_date date not null default public.local_today(),
  payment_type public.payment_type not null,
  installment_count integer not null,
  total_amount numeric(10, 2) not null check (total_amount > 0),
  source public.purchase_source not null default 'app',
  created_by uuid default auth.uid() references auth.users (id),
  -- Filled when the purchase came from the WhatsApp bot.
  created_by_phone text,
  canceled_at timestamptz,
  notes text,
  created_at timestamptz not null default now(),
  constraint purchases_installment_count_check check (
    (payment_type = 'cash' and installment_count = 1)
    or (payment_type = 'installments' and installment_count between 2 and 24)
  )
);

create index purchases_customer_id_idx on public.purchases (customer_id);

create table public.purchase_items (
  id uuid primary key default gen_random_uuid(),
  purchase_id uuid not null references public.purchases (id) on delete cascade,
  product_id uuid not null references public.products (id),
  quantity integer not null check (quantity >= 1),
  -- Product price on the day of the sale; later price changes do not touch it.
  unit_price numeric(10, 2) not null check (unit_price > 0),
  subtotal numeric(10, 2) generated always as (quantity * unit_price) stored,
  created_at timestamptz not null default now()
);

create index purchase_items_purchase_id_idx on public.purchase_items (purchase_id);

create table public.installments (
  id uuid primary key default gen_random_uuid(),
  purchase_id uuid not null references public.purchases (id) on delete cascade,
  number integer not null check (number >= 1),
  amount numeric(10, 2) not null check (amount > 0),
  due_date date not null,
  -- Null while the installment is open.
  paid_at timestamptz,
  created_at timestamptz not null default now(),
  unique (purchase_id, number)
);

create index installments_purchase_id_idx on public.installments (purchase_id);
create index installments_open_due_date_idx
  on public.installments (due_date) where paid_at is null;

-- ---------------------------------------------------------------------------
-- Balances: what each active customer owes (open installments of
-- purchases that were not canceled).
-- ---------------------------------------------------------------------------

create view public.customer_balances
with (security_invoker = true) as
select
  c.id as customer_id,
  c.name,
  c.phone,
  coalesce(sum(i.amount) filter (where i.paid_at is null), 0)::numeric(10, 2)
    as open_amount,
  coalesce(sum(i.amount) filter (
    where i.paid_at is null and i.due_date < public.local_today()
  ), 0)::numeric(10, 2) as overdue_amount,
  min(i.due_date) filter (where i.paid_at is null) as next_due_date
from public.customers c
left join public.purchases p
  on p.customer_id = c.id and p.canceled_at is null
left join public.installments i
  on i.purchase_id = p.id
where c.deleted_at is null
group by c.id, c.name, c.phone;

-- ---------------------------------------------------------------------------
-- create_purchase: inserts a purchase, its items and its installments in one
-- transaction. Prices come from the products table (snapshot of the day).
--
-- p_items: [{"product_id": "<uuid>", "quantity": 2}, ...]
-- p_installment_count: 1 = cash (no installments), 2..24 = installments.
-- Installments: total / count rounded down to the cent; the last one takes
-- the difference. The first is due one month after the purchase (or on
-- p_first_due_date) and the next ones on the same day of the following
-- months (clamped to the last day of shorter months).
-- ---------------------------------------------------------------------------

create or replace function public.create_purchase(
  p_customer_id uuid,
  p_items jsonb,
  p_installment_count integer,
  p_purchase_date date default null,
  p_first_due_date date default null,
  p_source public.purchase_source default 'app',
  p_created_by_phone text default null,
  p_notes text default null
)
returns uuid
language plpgsql
set search_path = ''
as $$
declare
  v_purchase_id uuid;
  v_purchase_date date := coalesce(p_purchase_date, public.local_today());
  v_first_due_date date;
  v_item_count integer;
  v_total numeric(10, 2);
  v_base numeric(10, 2);
begin
  if p_items is null
     or jsonb_typeof(p_items) <> 'array'
     or jsonb_array_length(p_items) = 0 then
    raise exception 'A purchase needs at least one item' using errcode = '22023';
  end if;

  if p_installment_count is null
     or p_installment_count < 1
     or p_installment_count > 24 then
    raise exception 'Installment count must be between 1 and 24' using errcode = '22023';
  end if;

  if not exists (
    select 1 from public.customers
    where id = p_customer_id and deleted_at is null
  ) then
    raise exception 'Customer not found' using errcode = 'P0002';
  end if;

  select count(*), sum((e ->> 'quantity')::integer * pr.price)
    into v_item_count, v_total
  from jsonb_array_elements(p_items) e
  join public.products pr
    on pr.id = (e ->> 'product_id')::uuid and pr.active
  where (e ->> 'quantity')::integer >= 1;

  if v_item_count <> jsonb_array_length(p_items) then
    raise exception 'Invalid product or quantity' using errcode = '22023';
  end if;

  insert into public.purchases (
    customer_id, purchase_date, payment_type, installment_count,
    total_amount, source, created_by_phone, notes
  )
  values (
    p_customer_id,
    v_purchase_date,
    case when p_installment_count = 1
      then 'cash'::public.payment_type
      else 'installments'::public.payment_type
    end,
    p_installment_count,
    v_total,
    coalesce(p_source, 'app'),
    p_created_by_phone,
    nullif(trim(p_notes), '')
  )
  returning id into v_purchase_id;

  insert into public.purchase_items (purchase_id, product_id, quantity, unit_price)
  select v_purchase_id, pr.id, (e ->> 'quantity')::integer, pr.price
  from jsonb_array_elements(p_items) e
  join public.products pr on pr.id = (e ->> 'product_id')::uuid;

  if p_installment_count > 1 then
    v_first_due_date := coalesce(
      p_first_due_date,
      (v_purchase_date + interval '1 month')::date
    );
    v_base := floor(v_total * 100 / p_installment_count) / 100;

    insert into public.installments (purchase_id, number, amount, due_date)
    select
      v_purchase_id,
      n,
      case when n = p_installment_count
        then v_total - v_base * (p_installment_count - 1)
        else v_base
      end,
      (v_first_due_date + make_interval(months => n - 1))::date
    from generate_series(1, p_installment_count) as n;
  end if;

  return v_purchase_id;
end;
$$;

-- cancel_purchase: a canceled purchase stops counting in the balance but
-- stays in the history. Payments must be undone first.
create or replace function public.cancel_purchase(p_purchase_id uuid)
returns void
language plpgsql
set search_path = ''
as $$
begin
  if exists (
    select 1 from public.installments
    where purchase_id = p_purchase_id and paid_at is not null
  ) then
    raise exception 'Undo the paid installments before canceling'
      using errcode = 'P0001', hint = 'purchase_has_paid_installments';
  end if;

  update public.purchases
     set canceled_at = now()
   where id = p_purchase_id and canceled_at is null;

  if not found then
    raise exception 'Purchase not found or already canceled' using errcode = 'P0002';
  end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- Bot tables
-- ---------------------------------------------------------------------------

-- WhatsApp numbers allowed to register purchases through the bot.
create table public.authorized_senders (
  id uuid primary key default gen_random_uuid(),
  phone text not null unique check (phone ~ '^\+[1-9][0-9]{7,14}$'),
  name text not null,
  active boolean not null default true,
  created_at timestamptz not null default now()
);

-- Every message the webhook receives. wa_message_id is unique so a message
-- that WhatsApp delivers twice is processed only once.
create table public.whatsapp_messages (
  id uuid primary key default gen_random_uuid(),
  wa_message_id text not null unique,
  from_phone text not null,
  body text,
  status text not null default 'received'
    check (status in ('received', 'processed', 'ignored', 'error')),
  purchase_id uuid references public.purchases (id),
  error text,
  received_at timestamptz not null default now()
);

-- Conversation in progress with a sender (waiting for a confirmation or a
-- choice). Expires after 10 minutes.
create table public.bot_conversations (
  id uuid primary key default gen_random_uuid(),
  phone text not null unique,
  state text not null,
  draft jsonb not null default '{}'::jsonb,
  expires_at timestamptz not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create trigger bot_conversations_set_updated_at
  before update on public.bot_conversations
  for each row execute function public.set_updated_at();

-- ---------------------------------------------------------------------------
-- Access rules (RLS)
-- Signed-in app users have full access to the business tables.
-- Bot tables have no policies: only the service role (the webhook) uses them.
-- ---------------------------------------------------------------------------

alter table public.customers enable row level security;
alter table public.products enable row level security;
alter table public.purchases enable row level security;
alter table public.purchase_items enable row level security;
alter table public.installments enable row level security;
alter table public.authorized_senders enable row level security;
alter table public.whatsapp_messages enable row level security;
alter table public.bot_conversations enable row level security;

create policy "Signed-in users have full access" on public.customers
  for all to authenticated using (true) with check (true);
create policy "Signed-in users have full access" on public.products
  for all to authenticated using (true) with check (true);
create policy "Signed-in users have full access" on public.purchases
  for all to authenticated using (true) with check (true);
create policy "Signed-in users have full access" on public.purchase_items
  for all to authenticated using (true) with check (true);
create policy "Signed-in users have full access" on public.installments
  for all to authenticated using (true) with check (true);

revoke all on function public.create_purchase(uuid, jsonb, integer, date, date, public.purchase_source, text, text) from public, anon;
grant execute on function public.create_purchase(uuid, jsonb, integer, date, date, public.purchase_source, text, text) to authenticated, service_role;

revoke all on function public.cancel_purchase(uuid) from public, anon;
grant execute on function public.cancel_purchase(uuid) to authenticated, service_role;
