alter table if exists transaction
    add column if not exists batch_key          bytea      default null,
    add column if not exists inner_transactions bigint[] default null;
