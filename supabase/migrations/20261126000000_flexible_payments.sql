-- Flexible payments: a customer may pay any amount, not only whole
-- installments. A payment is spread over the customer's open installments,
-- oldest due date first (or starting at a chosen installment), so paying
-- more than one installment covers part of the next one and paying less
-- leaves the installment partly open.

-- ---------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------

create table public.payments (
  id uuid primary key default gen_random_uuid(),
  customer_id uuid not null references public.customers (id),
  amount numeric(10, 2) not null check (amount > 0),
  paid_on date not null default public.local_today(),
  notes text,
  created_by uuid default auth.uid() references auth.users (id),
  created_at timestamptz not null default now()
);

create index payments_customer_id_idx on public.payments (customer_id);

-- How much of each payment went to each installment.
create table public.payment_allocations (
  id uuid primary key default gen_random_uuid(),
  payment_id uuid not null references public.payments (id) on delete cascade,
  installment_id uuid not null references public.installments (id) on delete cascade,
  amount numeric(10, 2) not null check (amount > 0),
  created_at timestamptz not null default now(),
  unique (payment_id, installment_id)
);

create index payment_allocations_installment_id_idx on public.payment_allocations (installment_id);

-- Sum of the allocations, kept by register_payment and delete_payment.
-- paid_at stays: it is set when the installment is fully paid.
alter table public.installments
  add column paid_amount numeric(10, 2) not null default 0;

alter table public.installments
  add constraint installments_paid_amount_check check (paid_amount >= 0 and paid_amount <= amount);

-- Installments already marked as paid become one payment each.
do $$
declare
  r record;
  v_payment_id uuid;
begin
  for r in
    select i.id, i.amount, i.paid_at, p.customer_id
    from public.installments i
    join public.purchases p on p.id = i.purchase_id
    where i.paid_at is not null
  loop
    insert into public.payments (customer_id, amount, paid_on)
    values (r.customer_id, r.amount, (r.paid_at at time zone 'America/Sao_Paulo')::date)
    returning id into v_payment_id;

    insert into public.payment_allocations (payment_id, installment_id, amount)
    values (v_payment_id, r.id, r.amount);
  end loop;

  update public.installments set paid_amount = amount where paid_at is not null;
end;
$$;

-- ---------------------------------------------------------------------------
-- Balances now count what is left of each installment.
-- ---------------------------------------------------------------------------

create or replace view public.customer_balances
with (security_invoker = true) as
select
  c.id as customer_id,
  c.name,
  c.phone,
  coalesce(sum(i.amount - i.paid_amount), 0)::numeric(10, 2) as open_amount,
  coalesce(sum(i.amount - i.paid_amount) filter (
    where i.due_date < public.local_today()
  ), 0)::numeric(10, 2) as overdue_amount,
  min(i.due_date) as next_due_date
from public.customers c
left join public.purchases p
  on p.customer_id = c.id and p.canceled_at is null
left join public.installments i
  on i.purchase_id = p.id and i.paid_amount < i.amount
where c.deleted_at is null
group by c.id, c.name, c.phone;

-- ---------------------------------------------------------------------------
-- register_payment: records a payment of any amount and spreads it over the
-- customer's open installments (only those of p_purchase_id when given),
-- starting at p_installment_id when given, then by due date. A payment larger
-- than what is open is refused.
-- ---------------------------------------------------------------------------

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

  insert into public.payments (customer_id, amount, paid_on, notes)
  values (p_customer_id, p_amount, v_paid_on, nullif(trim(p_notes), ''))
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

    insert into public.payment_allocations (payment_id, installment_id, amount)
    values (v_payment_id, r.id, v_take);

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

-- delete_payment: undoes a payment registered by mistake.
create or replace function public.delete_payment(p_payment_id uuid)
returns void
language plpgsql
set search_path = ''
as $$
begin
  update public.installments i
     set paid_amount = i.paid_amount - a.amount,
         paid_at = null
    from public.payment_allocations a
   where a.payment_id = p_payment_id
     and a.installment_id = i.id;

  delete from public.payments where id = p_payment_id;

  if not found then
    raise exception 'Payment not found' using errcode = 'P0002';
  end if;
end;
$$;

-- A purchase with any amount paid cannot be canceled until those payments are removed.
create or replace function public.cancel_purchase(p_purchase_id uuid)
returns void
language plpgsql
set search_path = ''
as $$
begin
  if exists (
    select 1 from public.installments
    where purchase_id = p_purchase_id and paid_amount > 0
  ) then
    raise exception 'Remove the payments before canceling'
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
-- Access rules
-- ---------------------------------------------------------------------------

alter table public.payments enable row level security;
alter table public.payment_allocations enable row level security;

create policy "Signed-in users have full access" on public.payments
  for all to authenticated using (true) with check (true);
create policy "Signed-in users have full access" on public.payment_allocations
  for all to authenticated using (true) with check (true);

revoke all on function public.register_payment(uuid, numeric, date, uuid, uuid, text) from public, anon;
grant execute on function public.register_payment(uuid, numeric, date, uuid, uuid, text) to authenticated, service_role;

revoke all on function public.delete_payment(uuid) from public, anon;
grant execute on function public.delete_payment(uuid) to authenticated, service_role;
