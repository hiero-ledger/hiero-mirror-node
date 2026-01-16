------------
-- functions
------------
drop function if exists extractNsFromFilePath(varchar);
create function extractNsFromFilePath(filePath varchar) returns bigint as
$$
declare
	basename varchar;
	secsStr  varchar;
	secs     bigint;
	nsecsStr varchar;
	nsecs    bigint;
begin
	-- get the basename from the file path, note the file extension is stripped
	-- it's a variant of the default Java Instant string, e.g., '2019-08-30T18_10_00.419072Z'
	basename := regexp_replace(filePath, '^.*/([^/]*?)(\.[^/.]+)?$', '\1');

	secsStr := split_part(basename, '.', 1);
	secsStr := translate(secsStr, 'T_', ' :');
	secs := extract(epoch from to_timestamp(secsStr, 'YYYY-MM-DD HH24:MI:SS'));

	nsecsStr := regexp_replace(basename, '.*\.([0-9]*)Z', '\1');
	nsecsStr := rpad(nsecsStr, 9, '0');
	nsecs := to_number(nsecsStr, '999999999');

	return secs * 1000000000 + nsecs;
end;
$$ language plpgsql;

drop function if exists getNextRecordFileConsensusStart(bigint);
-- given the record file id, find the immediate next record file and return its consensus_start
-- if no record file found, return bigint max
create function getNextRecordFileConsensusStart(currentId bigint) returns bigint as
$$
declare
	nextConsensusStart bigint;
begin
	select consensus_start
	into strict nextConsensusStart
	from t_record_files
	where id > currentId
	order by id
	limit 1;
	return nextConsensusStart;
exception
	when NO_DATA_FOUND then
		-- no record file found, use bigint max
		return 9223372036854775807;
end;
$$ language plpgsql;

drop function if exists updateConsensusEndForRecordFiles();
create function updateConsensusEndForRecordFiles() returns void as
$$
declare
	txnCursor          refcursor;
	recordFile         record;
	consensusEnd       bigint;
	currentNs          bigint;
	nextConsensusStart bigint;
begin
	open txnCursor no scroll for select consensus_ns from transaction order by consensus_ns;
	currentNs := 0;

	for recordFile in
		select id
		from t_record_files
		where consensus_end = 0
		order by id
		loop
			if currentNs is NULL then
				raise exception 'No transactions left in cursor, but there are still record files to process, current at id %', recordFile.id;
			end if;

			nextConsensusStart := getNextRecordFileConsensusStart(recordFile.id);
			while true
				loop
					consensusEnd := currentNs;
					fetch txnCursor into currentNs;
					if currentNs is NULL or currentNs >= nextConsensusStart then
						exit; -- exit the while loop
					end if;
				end loop;

			if consensusEnd = 0 then
				raise exception 'Fatal! no valid consensus_end found for record file id %', recordFile.id;
			end if;

			update t_record_files
			set consensus_end = consensusEnd
			where id = recordFile.id;
		end loop;
end
$$ language plpgsql;

-- set consensus_start to the ns extracted from file name if it's 0
update t_record_files
set consensus_start = extractNsFromFilePath(name)
where consensus_start = 0;

select updateConsensusEndForRecordFiles();

-- drop all functions
drop function if exists extractNsFromFilePath(varchar);
drop function if exists getNextRecordFileConsensusStart(bigint);
drop function if exists updateConsensusEndForRecordFiles();
