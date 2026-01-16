-- due to hiero migration, java migrations FQCN changed from com.hedera. prefix to org.hiero. prefix and caused
-- issue in async java migration checksum query. As part of the fix, incorrect rows need to be removed
with affected as (
  select description
  from flyway_schema_history
  where type = 'JDBC' and version is null
  group by description
  having count(distinct script) = 2
), incorrect as (
  select description
  from affected as a
  where (
    select checksum
    from flyway_schema_history
    where description = a.description and script like 'com.hedera.%'
    order by installed_rank desc
    limit 1
  ) > 0 and (
    select checksum
    from flyway_schema_history
    where description = a.description and script like 'org.hiero.%'
    order by installed_rank desc
    limit 1
  ) < 0
)
delete from flyway_schema_history as f
using incorrect as i
where f.description = i.description and script like 'org.hiero.%';
