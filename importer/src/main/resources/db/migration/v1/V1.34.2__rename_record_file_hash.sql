/*
 * Reorganize hash columns in record_file table to make columns hash and prev_hash to store the chained hash of
 * the current file and the previous file respectively
 */

alter table if exists record_file rename column file_hash to hash;
alter table if exists record_file add column if not exists file_hash character varying(96);
update record_file set file_hash = hash;

create or replace function migrate_record_file_v5_hash() returns void as
$$
declare
	recordFileCursor refcursor;
	recordFile       record;
begin
	open recordFileCursor no scroll for select end_running_hash, version
										from record_file
										order by consensus_end desc
										for update;

	while true
		loop
			fetch recordFileCursor into recordFile;
			if not FOUND or recordFile.version != 5 then
				exit;
			end if;

			-- set v5 record file's hash column
			update record_file
			set hash = recordFile.end_running_hash
			where current of recordFileCursor;
		end loop;

	close recordFileCursor;
end;
$$ language plpgsql;

select migrate_record_file_v5_hash();
drop function if exists migrate_record_file_v5_hash();

alter table if exists record_file drop column if exists end_running_hash;

