-- due to hiero migration, java migrations FQCN changed from com.hedera. prefix to org.hiero. prefix and caused
-- issue in async java migration checksum query. As part of the fix, incorrect rows need to be removed
delete from flyway_schema_history where script like 'org.hiero.%';
