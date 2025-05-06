create table if not exists contract_action
(
    call_depth          integer                             not null,
    call_type           integer                             not null,
    caller              bigint                              not null,
    caller_type         entity_type     default 'CONTRACT'  not null,
    consensus_timestamp bigint                              not null,
    gas                 bigint                              not null,
    gas_used            bigint                              not null,
    index               integer                             not null,
    input               bytea                               null,
    recipient_account   bigint                              null,
    recipient_address   bytea                               null,
    recipient_contract  bigint                              null,
    result_data         bytea                               null,
    result_data_type    integer                             not null,
    value               bigint                              not null,
    primary key (consensus_timestamp, index)
);

alter table if exists contract
    add column if not exists runtime_bytecode bytea null;

alter table if exists contract_history
    add column if not exists runtime_bytecode bytea null;
