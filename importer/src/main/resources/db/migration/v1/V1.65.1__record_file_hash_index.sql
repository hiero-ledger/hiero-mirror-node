drop index if exists idx_file_data_hash_unq;
drop index if exists idx_file_data_prev_hash_unq;

create unique index if not exists record_file__hash
    on record_file (hash collate "C");
