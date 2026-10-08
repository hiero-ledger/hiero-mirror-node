alter table if exists ledger
    drop column if exists history_proof_verification_key,
    drop column if exists node_contributions;
