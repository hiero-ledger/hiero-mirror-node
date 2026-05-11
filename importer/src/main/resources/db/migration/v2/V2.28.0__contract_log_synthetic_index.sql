-- Partial index for synthetic contract_log rows used by the /contracts/results UNION query.
create index if not exists contract_log__synthetic_timestamp
    on contract_log (consensus_timestamp desc, index desc)
    where synthetic = true;
