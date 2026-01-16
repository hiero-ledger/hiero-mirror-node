--
 -- Add validDuration and maxFee columns to t_transactions
 --

alter table t_transactions
    add column if not exists valid_duration_seconds bigint null;

alter table t_transactions
    add column if not exists max_fee hbar_tinybars null;
