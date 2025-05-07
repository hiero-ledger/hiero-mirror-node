drop view if exists transaction_hash;

alter table transaction_hash_sharded rename to transaction_hash;

alter table transaction_hash_sharded_00 rename to transaction_hash_00;
alter table transaction_hash_sharded_01 rename to transaction_hash_01;
alter table transaction_hash_sharded_02 rename to transaction_hash_02;
alter table transaction_hash_sharded_03 rename to transaction_hash_03;
alter table transaction_hash_sharded_04 rename to transaction_hash_04;
alter table transaction_hash_sharded_05 rename to transaction_hash_05;
alter table transaction_hash_sharded_06 rename to transaction_hash_06;
alter table transaction_hash_sharded_07 rename to transaction_hash_07;
alter table transaction_hash_sharded_08 rename to transaction_hash_08;
alter table transaction_hash_sharded_09 rename to transaction_hash_09;
alter table transaction_hash_sharded_10 rename to transaction_hash_10;
alter table transaction_hash_sharded_11 rename to transaction_hash_11;
alter table transaction_hash_sharded_12 rename to transaction_hash_12;
alter table transaction_hash_sharded_13 rename to transaction_hash_13;
alter table transaction_hash_sharded_14 rename to transaction_hash_14;
alter table transaction_hash_sharded_15 rename to transaction_hash_15;
alter table transaction_hash_sharded_16 rename to transaction_hash_16;
alter table transaction_hash_sharded_17 rename to transaction_hash_17;
alter table transaction_hash_sharded_18 rename to transaction_hash_18;
alter table transaction_hash_sharded_19 rename to transaction_hash_19;
alter table transaction_hash_sharded_20 rename to transaction_hash_20;
alter table transaction_hash_sharded_21 rename to transaction_hash_21;
alter table transaction_hash_sharded_22 rename to transaction_hash_22;
alter table transaction_hash_sharded_23 rename to transaction_hash_23;
alter table transaction_hash_sharded_24 rename to transaction_hash_24;
alter table transaction_hash_sharded_25 rename to transaction_hash_25;
alter table transaction_hash_sharded_26 rename to transaction_hash_26;
alter table transaction_hash_sharded_27 rename to transaction_hash_27;
alter table transaction_hash_sharded_28 rename to transaction_hash_28;
alter table transaction_hash_sharded_29 rename to transaction_hash_29;
alter table transaction_hash_sharded_30 rename to transaction_hash_30;
alter table transaction_hash_sharded_31 rename to transaction_hash_31;

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
:= concat('transaction_hash_', to_char(mod(get_byte($1, 0), 32), 'fm00'));
RETURN QUERY EXECUTE 'SELECT * from ' || shard || ' WHERE hash = $1'
        USING $1;
END
$$ LANGUAGE plpgsql;

insert into transaction_hash
select *
from transaction_hash_old tho
where not exists (select get_transaction_info_by_hash(tho.hash));

drop table transaction_hash_old;

