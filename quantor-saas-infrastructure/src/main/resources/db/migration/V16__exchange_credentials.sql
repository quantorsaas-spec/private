create table if not exists user_exchange_credentials (
  id uuid primary key,
  user_id uuid not null,
  exchange varchar(32) not null, -- BINANCE for now
  label varchar(64) not null,    -- "main", "bot-1", etc

  api_key_enc text not null,
  api_secret_enc text not null,

  api_key_last4 varchar(4) not null,
  api_secret_last4 varchar(4) not null,

  created_at timestamptz not null default now(),
  deleted_at timestamptz null,

  constraint uq_user_exchange_label unique (user_id, exchange, label)
);

create index if not exists ix_u_ec_user on user_exchange_credentials(user_id);
