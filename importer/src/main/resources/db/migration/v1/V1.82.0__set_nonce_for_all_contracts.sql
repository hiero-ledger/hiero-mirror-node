-- set nonce to null for all contracts in the entity and entity_history tables
update entity
set ethereum_nonce = null
where type='CONTRACT';
update entity_history
set ethereum_nonce = null
where type='CONTRACT';
