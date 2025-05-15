alter table if exists node
    add column if not exists grpc_proxy_endpoint jsonb null default null;
alter table if exists node_history
    add column if not exists grpc_proxy_endpoint jsonb null default null;

