-- Remove address_book_entry_consensus_timestamp_fkey foreign key
alter table address_book_entry
    drop constraint if exists address_book_entry_consensus_timestamp_fkey;
