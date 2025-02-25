drop index if exists entity__id_type;

create index entity__type_id on entity(type, id);
