-------------------
-- Change transaction.node_account_id to no longer be required. This change is necessary since scheduled transactions
-- aren't associated with a particular node.
-------------------

alter table if exists transaction
    alter column node_account_id drop not null;
