-- Fill in the ed25519_public_key_hex from the raw 'key' column.
-- From BasicTypes.proto, message Key, extract the ED25519 key as hex.
-- This will convert conformant keys (protobuf option #2, length = 32 bytes)
-- NOTE: if the DB has many entities, this may need to be broken up into batches.
update t_entities
    -- This is where the key data starts in the protobuf (and fixes a bug in migration V1.11.0)
    -- Index here are 1-based, not 0-based.
    -- Skip the 4 byte protobuf header
    set ed25519_public_key_hex = substring(encode(key::bytea, 'hex'), 5)
    where key is not null
        and encode(key::bytea, 'hex') like '1220%'; -- This indicates it's a single ED25519 key of proper length.
