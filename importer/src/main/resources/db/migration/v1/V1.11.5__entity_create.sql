-- Full list of parameters required in `drop function` prior to postgresql 10.
drop function if exists f_entity_create(bigint, bigint, bigint, integer,
    bigint, bigint, bigint, bigint, character varying, bytea, bigint);

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
      AND   entity_num = _num;

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