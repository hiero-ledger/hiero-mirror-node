with extracted as (
  select
    id,
    (select key_info[2] from (
      select coalesce(
        -- ECDSA key - primitive, key list with one key, or 1/1 threshold key
        regexp_match(hex_key, '^(3a21|32250a233a21|2a29080112250a233a21)([A-Fa-f0-9]{66})$'),
        -- ED25519 key - primitive, key list with one key, or 1/1 threshold key
        regexp_match(hex_key, '^(1220|32240a221220|2a28080112240a221220)([A-Fa-f0-9]{64})$'),
        array[null::text, null::text]
      ) as key_info
      from (select encode(key, 'hex') as hex_key) as i
    ) as t) as correct_public_key
  from entity
)
update entity
set public_key = correct_public_key
from extracted
where entity.id = extracted.id
  and coalesce(correct_public_key, public_key) is not null
  and coalesce(correct_public_key <> public_key, true);

with extracted as (
  select
    id,
    (select key_info[2] from (
      select coalesce(
        -- ECDSA key - primitive, key list with one key, or 1/1 threshold key
        regexp_match(hex_key, '^(3a21|32250a233a21|2a29080112250a233a21)([A-Fa-f0-9]{66})$'),
        -- ED25519 key - primitive, key list with one key, or 1/1 threshold key
        regexp_match(hex_key, '^(1220|32240a221220|2a28080112240a221220)([A-Fa-f0-9]{64})$'),
        array[null::text, null::text]
      ) as key_info
      from (select encode(key, 'hex') as hex_key) as i
    ) as t) as correct_public_key,
    lower(timestamp_range) as lower_timestamp
  from entity_history
)
update entity_history
set public_key = correct_public_key
from extracted
where entity_history.id = extracted.id
  and lower(timestamp_range) = lower_timestamp
  and coalesce(correct_public_key, public_key) is not null
  and coalesce(correct_public_key <> public_key, true);
