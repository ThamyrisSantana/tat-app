// Runs the Supabase migrations on PGlite with a minimal Supabase-like auth
// setup and checks that each seller only reaches her own rows.
import { PGlite } from '@electric-sql/pglite';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const dir = fileURLToPath(new URL('../migrations/', import.meta.url));
const m1 = readFileSync(dir + '20261125000000_initial_schema.sql', 'utf8');
const m2 = readFileSync(dir + '20261126000000_flexible_payments.sql', 'utf8');
const m3 = readFileSync(dir + '20261127000000_per_seller_data.sql', 'utf8');

const SUPABASE = `
create schema auth;
create table auth.users (id uuid primary key, created_at timestamptz not null default now());
create function auth.uid() returns uuid language sql stable
  as $$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
create role anon nologin;
create role authenticated nologin;
create role service_role nologin bypassrls;
grant usage on schema public, auth to anon, authenticated, service_role;
grant execute on function auth.uid() to anon, authenticated, service_role;
alter default privileges in schema public grant all on tables to anon, authenticated, service_role;
alter default privileges in schema public grant all on functions to anon, authenticated, service_role;
`;

const A = '00000000-0000-0000-0000-00000000000a';
const B = '00000000-0000-0000-0000-00000000000b';
let failures = 0;
const ok = (cond, label) => { console.log((cond ? 'PASS ' : 'FAIL ') + label); if (!cond) failures++; };

async function as(db, role, user) {
  await db.exec(`reset role; select set_config('request.jwt.claim.sub', '${user ?? ''}', false); set role ${role};`);
}
async function rows(db, sql, params) { return (await db.query(sql, params)).rows; }
async function fails(db, sql, params) {
  try { await db.query(sql, params); return null; } catch (e) { return e.message; }
}

// ---- Existing project: data made before the migration --------------------
{
  const db = new PGlite();
  await db.exec(SUPABASE);
  await db.exec(m1);
  await db.exec(m2);
  await db.exec(`insert into auth.users (id, created_at) values ('${A}', now() - interval '2 days'), ('${B}', now())`);

  await as(db, 'authenticated', A);
  const [c] = await rows(db, `insert into customers (name, phone) values ('Ana', '+5521977776666') returning id`);
  const [p] = await rows(db, `insert into products (name, price) values ('Camiseta', 60) returning id`);
  const [{ create_purchase: purchaseA }] = await rows(db,
    `select create_purchase($1, $2::jsonb, 3)`, [c.id, JSON.stringify([{ product_id: p.id, quantity: 1 }])]);
  const [{ register_payment: paymentA }] = await rows(db, `select register_payment($1, 25)`, [c.id]);
  await db.exec('reset role');

  await db.exec(m3);
  ok(true, 'migration runs on a project with data');

  const owners = await rows(db, `
    select 'customers' t, owner_id from customers union all select 'products', owner_id from products
    union all select 'purchases', owner_id from purchases union all select 'items', owner_id from purchase_items
    union all select 'installments', owner_id from installments union all select 'payments', owner_id from payments
    union all select 'allocations', owner_id from payment_allocations`);
  ok(owners.length === 10 &&owners.every(r => r.owner_id === A), 'existing rows go to the oldest account');

  // Seller B sees and touches nothing of A.
  await as(db, 'authenticated', B);
  for (const t of ['customers', 'products', 'purchases', 'purchase_items', 'installments', 'payments', 'payment_allocations', 'customer_balances']) {
    ok((await rows(db, `select * from ${t}`)).length === 0, `B sees no ${t} of A`);
  }
  ok((await fails(db, `select create_purchase($1, $2::jsonb, 1)`, [c.id, JSON.stringify([{ product_id: p.id, quantity: 1 }])]))?.includes('Customer not found'),
    'B cannot sell to A\'s customer');
  ok((await fails(db, `select register_payment($1, 10)`, [c.id]))?.includes('Customer not found'), 'B cannot pay A\'s customer');
  ok((await fails(db, `select cancel_purchase($1)`, [purchaseA]))?.includes('not found'), 'B cannot cancel A\'s sale');
  ok((await fails(db, `select delete_payment($1)`, [paymentA]))?.includes('not found'), 'B cannot delete A\'s payment');
  ok((await db.query(`update customers set name = 'x' where id = $1`, [c.id])).affectedRows === 0, 'B cannot edit A\'s customer');
  ok((await db.query(`delete from products where id = $1`, [p.id])).affectedRows === 0, 'B cannot delete A\'s product');
  ok((await fails(db, `insert into customers (name, phone, owner_id) values ('Fake', '+5511900000000', $1)`, [A]))?.includes('row-level security'),
    'B cannot create rows for A');
  ok((await fails(db, `insert into purchases (customer_id, payment_type, installment_count, total_amount) values ($1, 'cash', 1, 10)`, [c.id])) !== null,
    'B cannot attach a raw purchase to A\'s customer');

  // B's own data, with the same phone and product name as A.
  const [cb] = await rows(db, `insert into customers (name, phone) values ('Ana B', '+5521977776666') returning id, owner_id`);
  ok(cb.owner_id === B, 'B\'s new customer is hers, same phone as A\'s customer is allowed');
  const [pb] = await rows(db, `insert into products (name, price) values ('camiseta', 50) returning id`);
  ok(!!pb, 'same product name as A is allowed');
  ok((await fails(db, `insert into customers (name, phone) values ('Dup', '+5521977776666')`))?.includes('duplicate'), 'B still cannot repeat her own phone');
  ok((await fails(db, `select create_purchase($1, $2::jsonb, 1)`, [cb.id, JSON.stringify([{ product_id: p.id, quantity: 1 }])]))?.includes('Invalid product'),
    'B cannot sell A\'s product');
  const [{ create_purchase: purchaseB }] = await rows(db,
    `select create_purchase($1, $2::jsonb, 2)`, [cb.id, JSON.stringify([{ product_id: pb.id, quantity: 2 }])]);
  const [{ register_payment: paymentB }] = await rows(db, `select register_payment($1, 30)`, [cb.id]);
  const balB = await rows(db, `select open_amount::text from customer_balances`);
  ok(balB.length === 1 && balB[0].open_amount === '70.00', 'B\'s balance counts only her sale (100 - 30)');
  await db.query(`select delete_payment($1)`, [paymentB]);
  await db.query(`select cancel_purchase($1)`, [purchaseB]);
  ok(true, 'B can undo her payment and cancel her sale');

  // A still works and sees only her rows.
  await as(db, 'authenticated', A);
  ok((await rows(db, `select * from customers`)).length === 1, 'A sees only her customer');
  const bal = await rows(db, `select open_amount::text from customer_balances`);
  ok(bal[0].open_amount === '35.00', 'A\'s balance unchanged (60 - 25)');
  await db.query(`select register_payment($1, 5)`, [c.id]);
  ok((await rows(db, `select open_amount::text from customer_balances`))[0].open_amount === '30.00', 'A can keep registering payments');

  // Anonymous: nothing.
  await as(db, 'anon', null);
  ok((await fails(db, `select * from customers`)) !== null || (await rows(db, `select * from customers`)).length === 0, 'anon reads nothing');
  ok((await fails(db, `select create_purchase($1, '[]'::jsonb, 1)`, [c.id]))?.includes('permission denied'), 'anon cannot call functions');

  // Bot (service role): the sale goes to the customer's seller.
  await as(db, 'service_role', null);
  const [{ create_purchase: botPurchase }] = await rows(db,
    `select create_purchase($1, $2::jsonb, 1, null, null, 'whatsapp', '+5511911112222')`, [cb.id, JSON.stringify([{ product_id: pb.id, quantity: 1 }])]);
  const [bp] = await rows(db, `select owner_id from purchases where id = $1`, [botPurchase]);
  const items = await rows(db, `select owner_id from purchase_items where purchase_id = $1`, [botPurchase]);
  ok(bp.owner_id === B && items.every(i => i.owner_id === B), 'bot sale belongs to the customer\'s seller');
  ok((await fails(db, `select create_purchase($1, $2::jsonb, 1)`, [cb.id, JSON.stringify([{ product_id: p.id, quantity: 1 }])]))?.includes('Invalid product'),
    'bot cannot mix sellers\' products');
  await db.close();
}

// ---- New project: migrations on an empty database -------------------------
{
  const db = new PGlite();
  await db.exec(SUPABASE);
  await db.exec(m1);
  await db.exec(m2);
  await db.exec(m3);
  ok(true, 'migrations run on an empty project without users');
  await db.exec(`insert into auth.users (id) values ('${A}')`);
  await as(db, 'authenticated', A);
  const [c] = await rows(db, `insert into customers (name, phone) values ('Ana', '+5521977776666') returning owner_id`);
  ok(c.owner_id === A, 'owner defaults to the signed-in seller');
  await db.close();
}

// ---- Data but no account: refused ----------------------------------------
{
  const db = new PGlite();
  await db.exec(SUPABASE);
  await db.exec(m1);
  await db.exec(m2);
  await db.exec(`insert into customers (name, phone) values ('Ana', '+5521977776666')`);
  let error = null;
  try { await db.exec(m3); } catch (e) { error = e.message; }
  ok(error?.includes('Create the shop owner'), 'data without any account is refused with a clear message');
  await db.close();
}

console.log(failures === 0 ? '\nALL PASSED' : `\n${failures} FAILED`);
process.exit(failures === 0 ? 0 : 1);
