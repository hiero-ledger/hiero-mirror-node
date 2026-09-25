create table if not exists record_file_watermark
(
    consensus_end bigint null
);
comment on table record_file_watermark is 'Single row: newest record file consensus_end whose distributed rows are committed';

insert into record_file_watermark (consensus_end)
select max(consensus_end)
from record_file;
