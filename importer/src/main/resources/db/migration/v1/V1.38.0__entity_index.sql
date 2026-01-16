-------------------
-- Update and add indexes for the entity table
-------------------

drop index if exists entity__public_key_natural_id;
create index if not exists entity__id_type on entity (id, type);
create index if not exists entity__public_key on entity (public_key) where public_key is not null;
