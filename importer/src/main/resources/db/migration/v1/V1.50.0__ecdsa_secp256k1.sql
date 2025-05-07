-- Support new primitive key type ECDSA_SECP256K1

alter table if exists transaction_signature
    add column if not exists type smallint;

-- Default existing rows to ED25519
update transaction_signature
set type = 3
where length(signature) = 64;

alter table if exists token
    drop column if exists fee_schedule_key_ed25519_hex,
    drop column if exists freeze_key_ed25519_hex,
    drop column if exists kyc_key_ed25519_hex,
    drop column if exists supply_key_ed25519_hex,
    drop column if exists wipe_key_ed25519_hex;

drop index if exists entity__shard_realm_num;
alter table if exists entity
    drop constraint if exists c__entity__lower_ed25519
