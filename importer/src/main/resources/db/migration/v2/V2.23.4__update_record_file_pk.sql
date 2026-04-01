ALTER TABLE record_file
DROP CONSTRAINT IF EXISTS record_file__pk,
ADD CONSTRAINT record_file__pk PRIMARY KEY (consensus_end);
