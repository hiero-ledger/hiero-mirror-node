create or replace function encodeEntityId(shard bigint, realm bigint, num bigint)
returns bigint as
$$
begin
    -- Encoding:
    -- 10 bits for shard (max = 1023), 16 bits for realm (max = 65535), 38 bits for num (max = 274877906943)
return (num & 274877906943) | ((realm & 65535) << 38) | ((shard & 1023) << 54);
end
$$ language plpgsql;