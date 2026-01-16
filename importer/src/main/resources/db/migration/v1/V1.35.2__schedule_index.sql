-- Add schedule index for creator_account_id

create index if not exists schedule__creator_account_id
    on schedule (creator_account_id desc);

