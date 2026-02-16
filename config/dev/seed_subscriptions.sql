-- File: Quantor/config/dev/seed_subscriptions.sql
create extension if not exists pgcrypto;

-- ensure user exists (пример, если нужно)
-- insert into users(id,email,created_at) values (...) on conflict do nothing;

-- upsert subscription
insert into subscriptions(
  id, user_id, plan, status, frozen, current_period_ends_at, external_subscription_id, updated_at
) values (
  gen_random_uuid(),
  '622af9a3-42b1-40e6-9c1d-d425a94679a0',
  'PRO',
  'ACTIVE',
  false,
  now() + interval '30 days',
  'dev-' || gen_random_uuid()::text,
  now()
)
on conflict (external_subscription_id) do update
set status='ACTIVE', current_period_ends_at=excluded.current_period_ends_at, updated_at=now();
