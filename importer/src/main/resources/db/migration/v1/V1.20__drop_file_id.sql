-- Drop id in t_record_files

ALTER TABLE IF EXISTS t_transactions
    DROP COLUMN IF EXISTS fk_rec_file_id;

DROP FUNCTION IF EXISTS f_file_create(_file_name t_record_files.name%TYPE);
DROP FUNCTION IF EXISTS f_file_complete (
    _file_id t_record_files.id%TYPE
    ,_file_hash t_record_files.file_hash%TYPE
    ,_prev_hash t_record_files.prev_hash%TYPE
);
