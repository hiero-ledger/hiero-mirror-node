-------------------
-- Fill missing contract file_id / initcode from its root contract
-- Last Updated: 07/27/2022
-------------------

with recursive descendant as (select id as root, id as parent, id
                              from contract
                              where file_id is not null
                                 or initcode is not null
                              union
                              select *
                              from (select d.root, d.id as parent, unnest(cr.created_contract_ids) as id
                                    from descendant d
                                             join contract_result cr on d.id = cr.contract_id
                                    where array_length(cr.created_contract_ids, 1) > 0) children
                              where parent <> id)
update contract c
set file_id  = r.file_id,
    initcode = r.initcode
from descendant d
         join contract r on r.id = d.root
where c.id = d.id
  and d.id <> d.parent
  and c.file_id is null
  and c.initcode is null;
