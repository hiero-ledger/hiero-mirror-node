DROP INDEX IF EXISTS idx_t_entities_unq;

WITH dupe_entities AS (
    SELECT * FROM (
        select id, min(id) OVER (PARTITION BY entity_shard, entity_realm, entity_num) AS newId FROM t_entities
    ) dupes WHERE id <> newId
),
update_cryptotransferlists AS (
    UPDATE t_cryptotransferlists c SET account_id = de.newId FROM dupe_entities de WHERE c.account_id = de.id
),
update_transactions_cud AS (
    UPDATE t_transactions t SET fk_cud_entity_id = de.newId FROM dupe_entities de WHERE t.fk_cud_entity_id = de.id
),
update_transactions_node AS (
    UPDATE t_transactions t SET fk_node_acc_id = de.newId FROM dupe_entities de WHERE t.fk_node_acc_id = de.id
),
update_transactions_payer AS (
    UPDATE t_transactions t SET fk_payer_acc_id = de.newId FROM dupe_entities de WHERE t.fk_payer_acc_id = de.id
)
DELETE FROM t_entities e USING dupe_entities de WHERE e.id = de.id;

CREATE UNIQUE INDEX idx_t_entities_unq ON t_entities (entity_shard, entity_realm, entity_num);
