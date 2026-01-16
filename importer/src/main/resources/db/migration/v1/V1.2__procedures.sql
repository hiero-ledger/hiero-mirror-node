CREATE FUNCTION f_file_create(varchar(250))
  RETURNS BIGINT AS
$$
DECLARE _file_name t_record_files.name%TYPE;
DECLARE file_id BIGINT;
BEGIN
  SELECT id
  INTO file_id
  FROM t_record_files
  WHERE name = _file_name;

  IF NOT FOUND THEN
    INSERT INTO t_record_files(name, load_start)
    VALUES (_file_name, extract(epoch from now()))
    RETURNING id INTO file_id;
  ELSE
    file_id = 0;
  END IF;

  RETURN file_id;
END;
$$ LANGUAGE plpgsql
;

GRANT ALL ON FUNCTION f_file_create(varchar(250)) TO ${db-user};

CREATE FUNCTION f_file_complete (
  _file_id t_record_files.id%TYPE
  ,_file_hash t_record_files.file_hash%TYPE
  ,_prev_hash t_record_files.prev_hash%TYPE
) RETURNS VOID AS $$
BEGIN
  UPDATE t_record_files
  SET load_end = extract(epoch from now())
    ,file_hash = _file_hash
    ,prev_hash = _prev_hash
  WHERE id = _file_id;
END
$$ LANGUAGE plpgsql
;

GRANT ALL ON FUNCTION f_file_complete (
  _file_id t_record_files.id%TYPE
  ,_file_hash t_record_files.file_hash%TYPE
  ,_prev_hash t_record_files.prev_hash%TYPE
) TO ${db-user};

CREATE FUNCTION f_entity_create (
  _shard t_entities.entity_shard%TYPE
  ,_realm t_entities.entity_realm%TYPE
  ,_num t_entities.entity_num%TYPE
  ,_type_id t_entities.fk_entity_type_id%TYPE
  ,_exp_time_sec t_entities.exp_time_seconds%TYPE
  ,_exp_time_nanos t_entities.exp_time_nanos%TYPE
  ,_exp_time_ns t_entities.exp_time_ns%TYPE
  ,_auto_renew t_entities.auto_renew_period%TYPE
  ,_admin_key BYTEA
  ,_key BYTEA
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
      , admin_key
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
      ,_admin_key
      ,_key
      ,CASE WHEN _proxy_acc_id = 0 THEN NULL ELSE _proxy_acc_id END

    RETURNING id INTO entity_id;
  END IF;

  RETURN entity_id;
END
$$ LANGUAGE plpgsql
;

GRANT ALL ON FUNCTION f_entity_create (
  _shard t_entities.entity_shard%TYPE
  ,_realm t_entities.entity_realm%TYPE
  ,_num t_entities.entity_num%TYPE
  ,_type_id t_entities.fk_entity_type_id%TYPE
  ,_exp_time_sec t_entities.exp_time_seconds%TYPE
  ,_exp_time_nanos t_entities.exp_time_nanos%TYPE
  ,_exp_time_ns t_entities.exp_time_ns%TYPE
  ,_auto_renew t_entities.auto_renew_period%TYPE
  ,_admin_key BYTEA
  ,_key BYTEA
  ,_proxy_acc_id t_entities.fk_prox_acc_id%TYPE
) TO ${db-user};
