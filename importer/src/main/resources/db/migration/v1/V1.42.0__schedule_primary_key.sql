-------------------
-- Change the primary key of schedule from consensus_timestamp to schedule_id
-------------------

drop index if exists schedule__schedule_id;

alter table if exists schedule
    drop constraint if exists schedule_pkey;

alter table if exists schedule
    add primary key (schedule_id);
