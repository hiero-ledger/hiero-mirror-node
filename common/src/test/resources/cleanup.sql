-- SPDX-License-Identifier: Apache-2.0

do
'
declare
    t record;
begin
for t in select table_name
from information_schema.tables
where table_schema = ''public'' and table_name !~ ''.*(flyway|transaction_type|citus_|_\d+).*'' and table_type <> ''VIEW''
    loop
    execute format(''delete from %s'', t.table_name);
    end loop;
end;
';
