do $$
declare
  current record;
  prior   record;
begin
for current in
      select * from nft
      union all
      select * from nft_history
      order by token_id, serial_number, timestamp_range
    loop
        if (prior is null) then
            prior = current;
            continue;
        elsif (current.token_id = prior.token_id and current.serial_number = prior.serial_number
                    and lower(current.timestamp_range) <> upper(prior.timestamp_range)) then
                insert into nft_history (
                    account_id,
                    created_timestamp,
                    delegating_spender,
                    deleted,
                    metadata,
                    serial_number,
                    spender,
                    timestamp_range,
                    token_id)
                values (
                    current.account_id,
                    current.created_timestamp,
                    null,
                    current.deleted,
                    current.metadata,
                    current.serial_number,
                    null,
                    int8range(upper(prior.timestamp_range), lower(current.timestamp_range), '[)'),
                    current.token_id);
        end if;
        prior = current;
    end loop;
end
$$ language plpgsql;