-- Rename transaction consensus_ns column to consistently uses consensus_timestamp
alter table transaction
    rename column consensus_ns to consensus_timestamp;