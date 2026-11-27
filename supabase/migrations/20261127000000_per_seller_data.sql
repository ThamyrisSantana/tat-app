-- Per-seller data: every row belongs to one account (owner_id) and each
-- signed-in seller sees and changes only her own customers, products, sales
-- and payments. Before this, every signed-in account saw everything.
--
-- Rows that already exist go to the oldest account in Authentication > Users
-- (the shop owner, when she is the only account so far).
--
-- Child rows (items, installments, payments, allocations) carry the owner too,
-- and composite foreign keys make sure they always have the same owner as the
-- row they point to, so no one can attach a sale to another seller's customer.

-- ---------------------------------------------------------------------------
-- owner_id on every table
-- ---------------------------------------------------------------------------

alter table public.customers add column owner_id uuid references auth.users (id);
alter table public.products add column owner_id uuid references auth.users (id);
alter table public.purchases add column owner_id uuid references auth.users (id);
alter table public.purchase_items add column owner_id uuid references auth.users (id);
alter table public.installments add column owner_id uuid references auth.users (id);
alter table public.payments add column owner_id uuid references auth.users (id);
alter table public.payment_allocations add column owner_id uuid references auth.users (id);
-- Bot: which seller a WhatsApp number sends for, and whose conversation it is.
alter table public.authorized_senders add column owner_id uuid references auth.users (id);
alter table public.bot_conversations add column owner_id uuid references auth.users (id);

do $$
declare
  v_owner uuid;
begin
  select id into v_owner from auth.users order by created_at, id limit 1;

  if v_owner is null then
    if exists (select 1 from public.customers)
       or exists (select 1 from public.products)
       or exists (select 1 from public.authorized_senders)
       or exists (select 1 from public.bot_conversations) then
      raise exception 'Create the shop owner''s account (Authentication > Users) before running this migration';
    end if;
    return;
  end if;

  update public.customers set owner_id = v_owner;
  update public.products set owner_id = v_owner;
  update public.purchases set owner_id = v_owner;
  update public.purchase_items set owner_id = v_owner;
  update public.installments set owner_id = v_owner;
  update public.payments set owner_id = v_owner;
  update public.payment_allocations set owner_id = v_owner;
  update public.authorized_senders set owner_id = v_owner;
  update public.bot_conversations set owner_id = v_owner;
end;
$$;

-- New rows made in the app belong to whoever is signed in. The functions
-- below set the owner explicitly, so they also work for the bot (service role).
alter table public.customers alter column owner_id set default auth.uid(), alter column owner_id set not null;
alter table public.products alter column owner_id set default auth.uid(), alter column owner_id set not null;
alter table public.purchases alter column owner_id set default auth.uid(), alter column owner_id set not null;
alter table public.purchase_items alter column owner_id set default auth.uid(), alter column owner_id set not null;
alter table public.installments alter column owner_id set default auth.uid(), alter column owner_id set not null;
alter table public.payments alter column owner_id set default auth.uid(), alter column owner_id set not null;
alter table public.payment_allocations alter column owner_id set default auth.uid(), alter column owner_id set not null;
alter table public.authorized_senders alter column owner_id set not null;
alter table public.bot_conversations alter column owner_id set not null;

create index customers_owner_id_idx on public.customers (owner_id);
create index products_owner_id_idx on public.products (owner_id);
create index purchases_owner_id_idx on public.purchases (owner_id);
create index purchase_items_owner_id_idx on public.purchase_items (owner_id);
create index installments_owner_id_idx on public.installments (owner_id);
create index payments_owner_id_idx on public.payments (owner_id);
create index payment_allocations_owner_id_idx on public.payment_allocations (owner_id);
create index authorized_senders_owner_id_idx on public.authorized_senders (owner_id);

-- ---------------------------------------------------------------------------
-- Child rows always have the same owner as their parent
-- ---------------------------------------------------------------------------

alter table public.customers add constraint customers_id_owner_key unique (id, owner_id);
alter table public.products add constraint products_id_owner_key unique (id, owner_id);
alter table public.purchases add constraint purchases_id_owner_key unique (id, owner_id);
alter table public.installments add constraint installments_id_owner_key unique (id, owner_id);
alter table public.payments add constraint payments_id_owner_key unique (id, owner_id);

alter table public.purchases
  add constraint purchases_customer_owner_fkey
  foreign key (customer_id, owner_id) references public.customers (id, owner_id);
alter table public.purchase_items
  add constraint purchase_items_purchase_owner_fkey
  foreign key (purchase_id, owner_id) references public.purchases (id, owner_id) on delete cascade;
alter table public.purchase_items
  add constraint purchase_items_product_owner_fkey
  foreign key (product_id, owner_id) references public.products (id, owner_id);
alter table public.installments
  add constraint installments_purchase_owner_fkey
  foreign key (purchase_id, owner_id) references public.purchases (id, owner_id) on delete cascade;
alter table public.payments
  add constraint payments_customer_owner_fkey
  foreign key (customer_id, owner_id) references public.customers (id, owner_id);
alter table public.payment_allocations
  add constraint payment_allocations_payment_owner_fkey
  foreign key (payment_id, owner_id) references public.payments (id, owner_id) on delete cascade;
alter table public.payment_allocations
  add constraint payment_allocations_installment_owner_fkey
  foreign key (installment_id, owner_id) references public.installments (id, owner_id) on delete cascade;

-- ---------------------------------------------------------------------------
-- Uniqueness is per seller: two sellers may have the same customer or a
-- product with the same name.
-- ---------------------------------------------------------------------------

drop index public.customers_phone_active_key;
create unique index customers_phone_active_key
  on public.customers (owner_id, phone) where deleted_at is null;

drop index public.products_name_active_key;
create unique index products_name_active_key
  on public.products (owner_id, lower(name)) where active;

-- authorized_senders.phone stays unique across sellers: the bot must know
-- whose sale a message is.

-- ---------------------------------------------------------------------------
-- Access rules: each seller only her own rows
-- ---------------------------------------------------------------------------

drop policy "Signed-in users have full access" on public.customers;
drop policy "Signed-in users have full access" on public.products;
drop policy "Signed-in users have full access" on public.purchases;
drop policy "Signed-in users have full access" on public.purchase_items;
drop policy "Signed-in users have full access" on public.installments;
drop policy "Signed-in users have full access" on public.payments;
drop policy "Signed-in users have full access" on public.payment_allocations;

-- (select auth.uid()) is evaluated once per query instead of once per row.
create policy "Sellers manage their own rows" on public.customers
  for all to authenticated
  using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));
create policy "Sellers manage their own rows" on public.products
  for all to authenticated
  using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));
create policy "Sellers manage their own rows" on public.purchases
  for all to authenticated
  using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));
create policy "Sellers manage their own rows" on public.purchase_items
  for all to authenticated
  using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));
create policy "Sellers manage their own rows" on public.installments
  for all to authenticated
  using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));
create policy "Sellers manage their own rows" on public.payments
  for all to authenticated
  using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));
create policy "Sellers manage their own rows" on public.payment_allocations
  for all to authenticated
  using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));

-- Bot tables keep no policies: only the service role (the webhook) uses them.

-- ---------------------------------------------------------------------------
-- Functions: the owner comes from the customer, so a seller can only sell to
-- and receive from her own customers (the app's RLS hides the others), and
-- the bot's sales land with the seller of that customer.
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
  v_owner uuid;
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

  select owner_id into v_owner
  from public.customers
  where id = p_customer_id and deleted_at is null;

  if v_owner is null then
    raise exception 'Customer not found' using errcode = 'P0002';
  end if;

  select count(*), sum((e ->> 'quantity')::integer * pr.price)
    into v_item_count, v_total
  from jsonb_array_elements(p_items) e
  join public.products pr
    on pr.id = (e ->> 'product_id')::uuid and pr.active and pr.owner_id = v_owner
  where (e ->> 'quantity')::integer >= 1;

  if v_item_count <> jsonb_array_length(p_items) then
    raise exception 'Invalid product or quantity' using errcode = '22023';
  end if;

  insert into public.purchases (
    owner_id, customer_id, purchase_date, payment_type, installment_count,
    total_amount, source, created_by_phone, notes
  )
  values (
    v_owner,
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

  insert into public.purchase_items (owner_id, purchase_id, product_id, quantity, unit_price)
  select v_owner, v_purchase_id, pr.id, (e ->> 'quantity')::integer, pr.price
  from jsonb_array_elements(p_items) e
  join public.products pr on pr.id = (e ->> 'product_id')::uuid and pr.owner_id = v_owner;

  if p_installment_count > 1 then
    v_first_due_date := coalesce(
      p_first_due_date,
      (v_purchase_date + interval '1 month')::date
    );
    v_base := floor(v_total * 100 / p_installment_count) / 100;

    insert into public.installments (owner_id, purchase_id, number, amount, due_date)
    select
      v_owner,
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

create or replace function public.register_payment(
  p_customer_id uuid,
  p_amount numeric,
  p_paid_on date default null,
  p_purchase_id uuid default null,
  p_installment_id uuid default null,
  p_notes text default null
)
returns uuid
language plpgsql
set search_path = ''
as $$
declare
  v_owner uuid;
  v_payment_id uuid;
  v_paid_on date := coalesce(p_paid_on, public.local_today());
  v_left numeric(10, 2) := p_amount;
  v_open numeric(10, 2);
  v_take numeric(10, 2);
  r record;
begin
  if p_amount is null or p_amount <= 0 or p_amount <> round(p_amount, 2) then
    raise exception 'Payment amount must be positive with at most 2 decimals' using errcode = '22023';
  end if;

  select owner_id into v_owner from public.customers where id = p_customer_id;

  if v_owner is null then
    raise exception 'Customer not found' using errcode = 'P0002';
  end if;

  select coalesce(sum(i.amount - i.paid_amount), 0)
    into v_open
  from public.installments i
  join public.purchases p on p.id = i.purchase_id
  where p.customer_id = p_customer_id
    and p.canceled_at is null
    and i.paid_amount < i.amount
    and (p_purchase_id is null or p.id = p_purchase_id);

  if p_amount > v_open then
    raise exception 'Payment is larger than the open amount'
      using errcode = 'P0001', hint = 'payment_exceeds_open_amount';
  end if;

  insert into public.payments (owner_id, customer_id, amount, paid_on, notes)
  values (v_owner, p_customer_id, p_amount, v_paid_on, nullif(trim(p_notes), ''))
  returning id into v_payment_id;

  for r in
    select i.id, i.amount - i.paid_amount as remaining
    from public.installments i
    join public.purchases p on p.id = i.purchase_id
    where p.customer_id = p_customer_id
      and p.canceled_at is null
      and i.paid_amount < i.amount
      and (p_purchase_id is null or p.id = p_purchase_id)
    order by (i.id = p_installment_id) desc nulls last, i.due_date, p.created_at, i.number
    for update of i
  loop
    exit when v_left <= 0;
    v_take := least(v_left, r.remaining);

    insert into public.payment_allocations (owner_id, payment_id, installment_id, amount)
    values (v_owner, v_payment_id, r.id, v_take);

    update public.installments
       set paid_amount = paid_amount + v_take,
           paid_at = case
             when paid_amount + v_take >= amount
               then (v_paid_on::timestamp at time zone 'America/Sao_Paulo')
             else null
           end
     where id = r.id;

    v_left := v_left - v_take;
  end loop;

  return v_payment_id;
end;
$$;

-- cancel_purchase and delete_payment need no change: they run with the
-- caller's rights, so RLS already limits them to the seller's own rows.
