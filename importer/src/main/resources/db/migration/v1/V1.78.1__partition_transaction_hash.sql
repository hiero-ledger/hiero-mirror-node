ALTER table transaction_hash RENAME to transaction_hash_old;

CREATE TABLE IF NOT EXISTS public.transaction_hash_sharded
(
    LIKE public.transaction_hash_old INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING STORAGE INCLUDING COMMENTS INCLUDING INDEXES
)
    PARTITION BY list (mod(get_byte(hash, 0), 32));

CREATE TABLE IF NOT EXISTS transaction_hash_sharded_00
PARTITION OF public.transaction_hash_sharded FOR VALUES in (0);
CREATE TABLE IF NOT EXISTS transaction_hash_sharded_01
PARTITION OF public.transaction_hash_sharded FOR VALUES in (1);
CREATE TABLE IF NOT EXISTS transaction_hash_sharded_02
PARTITION OF public.transaction_hash_sharded FOR VALUES in (2);
CREATE TABLE IF NOT EXISTS transaction_hash_sharded_03
PARTITION OF public.transaction_hash_sharded FOR VALUES in (3);
CREATE TABLE IF NOT EXISTS transaction_hash_sharded_04
PARTITION OF public.transaction_hash_sharded FOR VALUES in (4);
CREATE TABLE IF NOT EXISTS transaction_hash_sharded_05
PARTITION OF public.transaction_hash_sharded FOR VALUES in (5);
CREATE TABLE IF NOT EXISTS transaction_hash_sharded_06
PARTITION OF public.transaction_hash_sharded FOR VALUES in (6);
CREATE TABLE IF NOT EXISTS transaction_hash_sharded_07
PARTITION OF public.transaction_hash_sharded FOR VALUES in (7);
CREATE TABLE IF NOT EXISTS transaction_hash_sharded_08
PARTITION OF public.transaction_hash_sharded FOR VALUES in (8);
CREATE TABLE IF NOT EXISTS transaction_hash_sharded_09
PARTITION OF public.transaction_hash_sharded FOR VALUES in (9);
CREATE TABLE IF NOT EXISTS transaction_hash_sharded_10
PARTITION OF public.transaction_hash_sharded FOR VALUES in (10);
CREATE TABLE IF NOT EXISTS transaction_hash_sharded_11
PARTITION OF public.transaction_hash_sharded FOR VALUES in (11);
CREATE TABLE IF NOT EXISTS transaction_hash_sharded_12
PARTITION OF public.transaction_hash_sharded FOR VALUES in (12);
CREATE TABLE IF NOT EXISTS transaction_hash_sharded_13
PARTITION OF public.transaction_hash_sharded FOR VALUES in (13);
CREATE TABLE IF NOT EXISTS transaction_hash_sharded_14
PARTITION OF public.transaction_hash_sharded FOR VALUES in (14);
CREATE TABLE IF NOT EXISTS transaction_hash_sharded_15
PARTITION OF public.transaction_hash_sharded FOR VALUES in (15);
CREATE TABLE IF NOT EXISTS transaction_hash_sharded_16
PARTITION OF public.transaction_hash_sharded FOR VALUES in (16);
CREATE TABLE IF NOT EXISTS transaction_hash_sharded_17
PARTITION OF public.transaction_hash_sharded FOR VALUES in (17);
CREATE TABLE IF NOT EXISTS transaction_hash_sharded_18
PARTITION OF public.transaction_hash_sharded FOR VALUES in (18);
CREATE TABLE IF NOT EXISTS transaction_hash_sharded_19
PARTITION OF public.transaction_hash_sharded FOR VALUES in (19);
CREATE TABLE IF NOT EXISTS transaction_hash_sharded_20
PARTITION OF public.transaction_hash_sharded FOR VALUES in (20);
CREATE TABLE IF NOT EXISTS transaction_hash_sharded_21
PARTITION OF public.transaction_hash_sharded FOR VALUES in (21);
CREATE TABLE IF NOT EXISTS transaction_hash_sharded_22
PARTITION OF public.transaction_hash_sharded FOR VALUES in (22);
CREATE TABLE IF NOT EXISTS transaction_hash_sharded_23
PARTITION OF public.transaction_hash_sharded FOR VALUES in (23);
CREATE TABLE IF NOT EXISTS transaction_hash_sharded_24
PARTITION OF public.transaction_hash_sharded FOR VALUES in (24);
CREATE TABLE IF NOT EXISTS transaction_hash_sharded_25
PARTITION OF public.transaction_hash_sharded FOR VALUES in (25);
CREATE TABLE IF NOT EXISTS transaction_hash_sharded_26
PARTITION OF public.transaction_hash_sharded FOR VALUES in (26);
CREATE TABLE IF NOT EXISTS transaction_hash_sharded_27
PARTITION OF public.transaction_hash_sharded FOR VALUES in (27);
CREATE TABLE IF NOT EXISTS transaction_hash_sharded_28
PARTITION OF public.transaction_hash_sharded FOR VALUES in (28);
CREATE TABLE IF NOT EXISTS transaction_hash_sharded_29
PARTITION OF public.transaction_hash_sharded FOR VALUES in (29);
CREATE TABLE IF NOT EXISTS transaction_hash_sharded_30
PARTITION OF public.transaction_hash_sharded FOR VALUES in (30);
CREATE TABLE IF NOT EXISTS transaction_hash_sharded_31
PARTITION OF public.transaction_hash_sharded FOR VALUES in (31);

CREATE OR REPLACE VIEW transaction_hash AS
SELECT * FROM transaction_hash_old UNION ALL SELECT * FROM transaction_hash_sharded;

CREATE OR REPLACE FUNCTION get_transaction_info_by_hash(bytea)
    returns TABLE
            (
                consensus_timestamp bigint,
                hash                bytea,
                payer_account_id    bigint
            )
AS
$$
DECLARE
    shard varchar;
BEGIN
    shard
        := concat('transaction_hash_sharded_', to_char(mod(get_byte($1, 0), 32), 'fm00'));
    RETURN QUERY EXECUTE 'SELECT * from transaction_hash_old where hash = $1 ' ||
                         'UNION ALL SELECT * from ' || shard ||
                         ' WHERE hash = $1'
        USING $1;
END
$$ LANGUAGE plpgsql;
