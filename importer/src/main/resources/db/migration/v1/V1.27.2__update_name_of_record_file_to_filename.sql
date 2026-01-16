-- update the name column of t_record_files table. if the name is the full path (begins with '/'),
-- the query will set it to the file name.
update t_record_files
set name = regexp_replace(name, '^.*/([^/]+)$', '\1')
where name like '/%.rcd';
