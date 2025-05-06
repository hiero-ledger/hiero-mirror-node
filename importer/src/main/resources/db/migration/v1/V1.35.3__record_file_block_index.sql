-------------------
-- Add column index, starting from 0 for record files by consensus_end in ascending order.
-- Also drop the id column and its sequence
-------------------

alter table if exists record_file
    drop column id,
    alter column consensus_start drop default,
    alter column consensus_end drop default;
drop index if exists idx_t_record_files_name, record_file__consensus_end;
drop sequence if exists s_record_files_seq;

alter table if exists record_file
    add column index bigint,
    add primary key (consensus_end);

with block as (
    select consensus_end as ce, row_number() over (order by consensus_end)
    from record_file
    order by consensus_end
)
update record_file set index = block.row_number - 1 from block where consensus_end = block.ce;

alter table if exists record_file alter column index set not null;
create unique index if not exists record_file__index on record_file (index);
