-------------------
-- Support HIP-18 custom fee enhancement: effective payers, net of transfers, and royalty fee
-------------------

-- update description for existing result code and add new ones
update t_transaction_results set result = 'ROYALTY_FRACTION_CANNOT_EXCEED_ONE'
where proto_id = 242;

insert into t_transaction_results (result, proto_id)
values ('INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE', 259),
       ('SERIAL_NUMBER_LIMIT_REACHED', 260),
       ('CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE', 261);

-- net_of_transfer, and royalty fraction
alter table if exists custom_fee
    add column net_of_transfers boolean,
    add column royalty_denominator bigint,
    add column royalty_numerator bigint;

-- set net_of_transfers of historical fractional fees to false
update custom_fee set net_of_transfers = false
where amount_denominator is not null;

-- effective payer account id of assessed custom fee
alter table if exists assessed_custom_fee
    add effective_payer_account_ids bigint[] not null default '{}';

alter table if exists assessed_custom_fee
    alter column effective_payer_account_ids drop default;
