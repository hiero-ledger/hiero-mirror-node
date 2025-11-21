alter table account_balance rename to account_balance_old;
alter table token_balance rename to token_balance_old;

do $$
declare
    r record;
begin
    for r in
        select
            n.nspname as schema_name,
            c.relname as child_name
        from pg_inherits i
        join pg_class c on c.oid = i.inhrelid
        join pg_class p on p.oid = i.inhparent
        join pg_namespace n on n.oid = c.relnamespace
        where p.relname = 'account_balance_old'
    loop
        execute format(
            'alter table %I.%I rename to %I',
            r.schema_name,
            r.child_name,
            r.child_name || '_old'
        );
    end loop;

    for r in
        select
            n.nspname as schema_name,
            c.relname as child_name
        from pg_inherits i
        join pg_class c on c.oid = i.inhrelid
        join pg_class p on p.oid = i.inhparent
        join pg_namespace n on n.oid = c.relnamespace
        where p.relname = 'token_balance_old'
    loop
        execute format(
            'alter table %I.%I rename to %I',
            r.schema_name,
            r.child_name,
            r.child_name || '_old'
        );
    end loop;
end;
$$ language plpgsql;

create table if not exists account_balance
(
    account_id          bigint not null,
    balance             bigint not null,
    consensus_timestamp bigint not null
) partition by range (consensus_timestamp);
comment on table account_balance is 'Account balances (historical) in tinybars at different consensus timestamps';

create table if not exists token_balance
(
    account_id          bigint not null,
    balance             bigint not null,
    consensus_timestamp bigint not null,
    token_id            bigint not null
) partition by range (consensus_timestamp);
comment on table token_balance is 'Crypto account token balances';

select create_time_partitions(table_name :='public.account_balance',
                              partition_interval := ${balancePartitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${balancePartitionTimeInterval});

select create_time_partitions(table_name :='public.token_balance',
                              partition_interval := ${balancePartitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${balancePartitionTimeInterval});

do $$
declare
    start_ts                timestamptz := ${partitionStartDate}::timestamptz;
    end_ts                  timestamptz;
    max_consensus_timestamp bigint;
    from_ns                 bigint;
    to_ns                   bigint;
    sentinel                bigint;

begin
    select greatest(
        coalesce((select max(consensus_timestamp) from account_balance_old), 0),
        coalesce((select max(consensus_timestamp) from token_balance_old), 0)
    )
    into max_consensus_timestamp;

    if max_consensus_timestamp = 0 then
        raise notice 'both account_balance_old and token_balance_old are empty; nothing to migrate';
        return;
    end if;

    select
        case
          when ${shard}::bigint = 0 and ${realm}::bigint = 0 then 2
          else
            (2 & ((1::bigint << 38) - 1))
            | ( (${realm}::bigint & ((1::bigint << 16) - 1)) << 38 )
            | ( (${shard}::bigint & ((1::bigint << 10) - 1)) << 54 )
        end
    into sentinel;

    while start_ts <= to_timestamp(max_consensus_timestamp / 1e9) loop
        end_ts  := start_ts + interval '6 months';
        from_ns := (extract(epoch from start_ts) * 1e9)::bigint;
        to_ns   := (extract(epoch from end_ts)   * 1e9)::bigint;

        raise notice 'migrating range [% %, % %)', from_ns, to_ns, start_ts, end_ts;

        insert into account_balance (account_id, balance, consensus_timestamp)
        select account_id, balance, consensus_timestamp
        from (
            select
                ab.account_id,
                ab.balance,
                ab.consensus_timestamp,
                lag(ab.balance) over (
                    partition by ab.account_id
                    order by ab.consensus_timestamp
                ) as prev_balance
            from account_balance_old ab
            where ab.consensus_timestamp >= from_ns
              and ab.consensus_timestamp <  to_ns
        ) t
        where t.account_id=sentinel
           or t.prebalance is null
           or t.prev_balance <> t.balance;

        insert into token_balance (account_id, balance, consensus_timestamp, token_id)
        select account_id, balance, consensus_timestamp, token_id
        from (
            select
                tb.account_id,
                tb.balance,
                tb.consensus_timestamp,
                tb.token_id,
                lag(tb.balance) over (
                    partition by tb.account_id, tb.token_id
                    order by tb.consensus_timestamp
                ) as prev_balance
            from token_balance_old tb
            where tb.consensus_timestamp >= from_ns
              and tb.consensus_timestamp <  to_ns
        ) t
        where t.prev_balance is null
           or t.prev_balance <> t.balance;
        start_ts := end_ts;
    end loop;
end;
$$ language plpgsql;

alter table if exists account_balance
    add constraint account_balance_new__pk primary key (account_id, consensus_timestamp);
alter table if exists token_balance
    add constraint token_balance_new__pk primary key (account_id, token_id, consensus_timestamp);
create index if not exists token_balance_new__token_account_timestamp
    on token_balance (token_id, account_id, consensus_timestamp);

create table if not exists account_balance_change
(
    account_id bigint not null
);

select create_distributed_table('account_balance_change', 'account_id', colocate_with => 'entity');

alter table if exists account_balance_change
    add constraint account_balance_change__pk primary key (account_id);

create table if not exists token_balance_change
(
    account_id bigint not null,
    token_id   bigint not null
);

select create_distributed_table('token_balance_change', 'account_id', colocate_with => 'entity');

alter table if exists token_balance_change
    add constraint token_balance_change__pk primary key (account_id, token_id);

insert into account_balance_change (account_id)
select id from entity
where balance_timestamp > (select max(consensus_timestamp) from account_balance_file)
order by id;

insert into token_balance_change (account_id, token_id)
select account_id, token_id
from token_account where balance_timestamp > (select max(consensus_timestamp) from account_balance_file)
order by account_id, token_id;

drop table if exists account_balance_old;
drop table if exists token_balance_old;