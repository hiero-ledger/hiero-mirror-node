alter table contract_log
    alter column topic0 type bytea using decode(topic0, 'hex'),
    alter column topic1 type bytea using decode(topic1, 'hex'),
    alter column topic2 type bytea using decode(topic2, 'hex'),
    alter column topic3 type bytea using decode(topic3, 'hex');
