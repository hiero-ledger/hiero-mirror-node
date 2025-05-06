create or replace procedure remove_incorrect_entity_stake()  as
$$
declare
  one_sec_in_ns constant bigint := 10^9;

  last_good_lower_timestamp bigint;
  migration_timestamp bigint;
begin
  -- The last known good entity stake's timestamp_range should contain the time when V1.89.2 was installed.
  select (extract(epoch from installed_on) * one_sec_in_ns)::bigint into migration_timestamp
  from flyway_schema_history
  where version = '1.89.2';
  if not found then
    return;
  end if;
  raise notice 'migration timestamp: %', migration_timestamp;

  select lower(timestamp_range) into last_good_lower_timestamp
  from entity_stake_history
  where id = 800 and timestamp_range @> migration_timestamp;
  if not found then
    return;
  end if;

  -- Copy the last known good entity_stake rows to the current table
  truncate table entity_stake;
  insert into entity_stake (decline_reward_start, end_stake_period, id, pending_reward, staked_node_id_start,
    staked_to_me, stake_total_start, timestamp_range)
  select
    decline_reward_start,
    end_stake_period,
    id,
    pending_reward,
    staked_node_id_start,
    staked_to_me,
    stake_total_start,
    int8range(lower(timestamp_range), null)
  from entity_stake_history
  where timestamp_range @> migration_timestamp;

  -- Delete incorrect history rows
  raise notice 'deleting entity_stake_history rows at or after: %', last_good_lower_timestamp;
  delete from entity_stake_history
  where timestamp_range in (
    select timestamp_range
    from entity_stake_history
    where id = 800 and lower(timestamp_range) >= last_good_lower_timestamp
  );
end;
$$ language plpgsql;

call remove_incorrect_entity_stake();

drop procedure if exists remove_incorrect_entity_stake();
