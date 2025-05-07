alter table token_account rename to token_account_old;

create table token_account (
  account_id            bigint    not null,
  associated            boolean   not null default false,
  automatic_association boolean   not null default false,
  created_timestamp     bigint    not null,
  freeze_status         smallint  not null default 0,
  kyc_status            smallint  not null default 0,
  timestamp_range       int8range not null,
  token_id              bigint    not null
);

create table token_account_history (
  like token_account including defaults
);

with merged as (
  select
    account_id,
    associated,
    automatic_association,
    created_timestamp,
    freeze_status,
    kyc_status,
    int8range(p.modified_timestamp, next.modified_timestamp) timestamp_range,
    token_id
  from token_account_old p
  left join lateral (
    select s.modified_timestamp
    from token_account_old s
    where s.account_id = p.account_id and s.token_id = p.token_id
      and s.modified_timestamp > p.modified_timestamp
    order by s.modified_timestamp
    limit 1
  ) next on true
), history as (
  insert into token_account_history
  select * from merged
  where upper(timestamp_range) is not null
)
insert into token_account
select * from merged
where upper(timestamp_range) is null;

drop table token_account_old;

alter table token_account add primary key (account_id, token_id);
alter table token_account_history add primary key (account_id, token_id, timestamp_range);