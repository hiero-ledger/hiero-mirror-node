-------------------
-- Support upsert (insert and update from temp table) capabilities for updatable domains
-------------------

-- allow nullable on entity deleted as transaction cannot make this assumption on updates
alter table entity
    alter column deleted drop default,
    alter column deleted drop not null;
