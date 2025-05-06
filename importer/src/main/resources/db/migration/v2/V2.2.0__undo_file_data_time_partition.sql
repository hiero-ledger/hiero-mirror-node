alter table if exists file_data rename to file_data_old;

create table if not exists file_data (like file_data_old including defaults);
select create_distributed_table('file_data', 'entity_id', colocate_with => 'entity');
insert into file_data select * from file_data_old;

drop table if exists file_data_old cascade;

create index if not exists file_data__id_timestamp on file_data (entity_id, consensus_timestamp);
