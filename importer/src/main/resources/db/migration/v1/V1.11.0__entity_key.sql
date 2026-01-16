-- Fix bad data where empty string was used instead of null for keys.
update t_entities
    set key = null
    where length(key) = 0;
update t_entities
    set admin_key = null
    where length(admin_key) = 0;

-- Consolidate the key and admin_key fields as they cannot both be present for the same entities.
update t_entities
    set key = admin_key
    where admin_key is not null
    and key is null;
alter table t_entities
    rename column admin_key to admin_key__deprecated;

-- Add a plaintext hex (lowercase) representation of ED25519 public key
alter table t_entities
    add column ed25519_public_key_hex varchar null;
-- Enforce lowercase hex representation by constraint rather than making indexes on lower(ed25519).
alter table t_entities
    add constraint c__t_entities__lower_ed25519
    check (ed25519_public_key_hex = lower(ed25519_public_key_hex));
create index idx__t_entities__ed25519_public_key_hex_natural_id
    on t_entities (ed25519_public_key_hex, fk_entity_type_id, entity_shard, entity_realm, entity_num);

-- Fill in the ed25519_public_key_hex from the raw 'key' column.
-- From BasicTypes.proto, message Key, extract the ED25519 key as hex.
-- This will convert conformant keys (protobuf option #2, length = 32 bytes)
-- NOTE: if the DB has many entities, this may need to be broken up into batches.
update t_entities
    set ed25519_public_key_hex = substring(encode(key::bytea, 'hex'), 4) -- This is where the key data starts in the protobuf
    where key is not null
        and encode(key::bytea, 'hex') like '1220%'; -- This indicates it's a single ED25519 key of proper length.

--
-- Update affected functions.
--

-- Full list of parameters required in `drop function` prior to postgresql 10.
drop function if exists f_entity_create(bigint, bigint, bigint, integer,
    bigint, bigint, bigint, bigint, bytea, bytea, bigint);

CREATE FUNCTION f_entity_create (
    _shard t_entities.entity_shard%TYPE
    ,_realm t_entities.entity_realm%TYPE
    ,_num t_entities.entity_num%TYPE
    ,_type_id t_entities.fk_entity_type_id%TYPE
    ,_exp_time_sec t_entities.exp_time_seconds%TYPE
    ,_exp_time_nanos t_entities.exp_time_nanos%TYPE
    ,_exp_time_ns t_entities.exp_time_ns%TYPE
    ,_auto_renew t_entities.auto_renew_period%TYPE
    ,_ed25519_public_key_hex t_entities.ed25519_public_key_hex%type
    ,_key t_entities.key%type
    ,_proxy_acc_id t_entities.fk_prox_acc_id%TYPE
) RETURNS BIGINT AS $$
DECLARE
    entity_id BIGINT;
BEGIN

    SELECT id
    INTO entity_id
    FROM t_entities
    WHERE entity_shard = _shard
      AND   entity_realm = _realm
      AND   entity_num = _num
      AND   fk_entity_type_id = _type_id;

    IF NOT FOUND THEN
        INSERT INTO t_entities (
                                 entity_shard
                               , entity_realm
                               , entity_num
                               , fk_entity_type_id
                               , exp_time_seconds
                               , exp_time_nanos
                               , exp_time_ns
                               , auto_renew_period
                               , ed25519_public_key_hex
                               , key
                               , fk_prox_acc_id
        )
        SELECT
            _shard
             ,_realm
             ,_num
             ,_type_id
             ,CASE WHEN _exp_time_sec = 0 THEN NULL ELSE _exp_time_sec END
             ,CASE WHEN _exp_time_nanos = 0 THEN NULL ELSE _exp_time_nanos END
             ,CASE WHEN _exp_time_ns = 0 THEN NULL ELSE _exp_time_ns END
             ,CASE WHEN _auto_renew = 0 THEN NULL ELSE _auto_renew END
             ,_ed25519_public_key_hex
             ,_key
             ,CASE WHEN _proxy_acc_id = 0 THEN NULL ELSE _proxy_acc_id END

            RETURNING id INTO entity_id;
    END IF;

    RETURN entity_id;
END
$$ LANGUAGE plpgsql
;