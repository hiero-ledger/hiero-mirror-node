-- Change entity public key index

drop index if exists entity__public_key;
create index if not exists entity__public_key_type on entity(public_key, type) where public_key is not null;
