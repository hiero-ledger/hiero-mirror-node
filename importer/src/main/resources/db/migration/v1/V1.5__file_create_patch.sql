DROP FUNCTION IF EXISTS f_file_create(varchar(250));

CREATE FUNCTION f_file_create(_file_name t_record_files.name%TYPE)
  RETURNS BIGINT AS
$$
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

GRANT ALL ON FUNCTION f_file_create(_file_name t_record_files.name%TYPE) TO ${db-user};