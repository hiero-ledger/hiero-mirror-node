#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0

set -emo pipefail

# Print log statements to stdout
log() {
  echo "$(date --iso-8601=seconds) ${1}"
}

# Query the source database and return the response
querySource() {
  local query="${1}"
  local result=$(PGPASSWORD="${SOURCE_DB_PASSWORD}" psql -q --csv -t -h "${SOURCE_DB_HOST}" -d "${SOURCE_DB_NAME}" -p "${SOURCE_DB_PORT}" -U "${SOURCE_DB_USER}" -c "${query}" | xargs)
  echo "${result}"
}

# Query the target database and return the response
queryTarget() {
  local query="${1}"
  local result=$(PGPASSWORD="${TARGET_DB_PASSWORD}" psql -q --csv -t -h "${TARGET_DB_HOST}" -d "${TARGET_DB_NAME}" -p "${TARGET_DB_PORT}" -U "${TARGET_DB_USER}" --single-transaction -c "${query}" | xargs)
  echo "${result}"
}

getMaxTimestamp() {
  local migrationEnv="$(dirname ${0})/migration.env"

  if [[ -f "${migrationEnv}" ]]; then
    # shellcheck source=/dev/null
    source "${migrationEnv}"
  fi

  if [[ -z "${MAX_TIMESTAMP}" ]]; then
    local query="select max(consensus_end) from record_file;"
    local maxTimestamp=$(querySource "${query}")

    if [[ -z "${maxTimestamp}" ]]; then
      die "Unable to determine the max timestamp from the source database"
    fi

    echo "MAX_TIMESTAMP=${maxTimestamp}" > "${migrationEnv}"
    echo "${maxTimestamp}"
  else
    echo "${MAX_TIMESTAMP}"
  fi

}

die() {
  log "$@"
  exit 1
}

handlePipeError() {
  log "ERROR: pid: $! on $$ SIGPIPE received on pipe $!{EPIPE} msg: ${1}"
  exit 1
}

handleExit() {
  local error_code=$?
  local error_line="${BASH_LINENO[*]}"
  local error_command=$BASH_COMMAND
  log "ERROR: pid: $! on $$ Received non zero exit code: (${error_code}) ${1} at ${error_line}: ${error_command}"
  exit 1
}

targetTableExists() {
  local table="${1}"
  local query="select exists (select from information_schema.tables where table_schema = '${TARGET_DB_SCHEMA}' and table_name = '${table}');"
  local exists=$(queryTarget "${query}")
  echo "${exists}"
}

getColumns() {
  local table="${1}"
  local query="select string_agg(concat('', column_name), ', ' order by ordinal_position) from information_schema.columns where table_schema = '${SOURCE_DB_SCHEMA}' and table_name = '${table}';"
  local columns
  columns=$(querySource "${query}")
  columns="${columns#"${columns%%[![:space:]]*}"}" # Trim leading whitespace
  echo "${columns}"
}

getCopySql() {
  local table="${1}"
  local columns="${2}"
  local where="${3}"
  local join="${4}"
  local selectColumns="${5:-$columns}"

  local sourceSql="PGPASSWORD=${SOURCE_DB_PASSWORD} psql -q -t -h ${SOURCE_DB_HOST} -d ${SOURCE_DB_NAME} -p ${SOURCE_DB_PORT} -U ${SOURCE_DB_USER} -c \"copy (select ${selectColumns} from ${table} ${join} ${where}) to stdout with binary\""
  local sql="\\copy ${table}(${columns}) from program '${sourceSql}' with binary;"
  echo "${sql}"
}

insertCursors() {
  local hasTable="f"
  local sqlTemp="/tmp/cursors-$$.csv"
  local tableDDL="
    create table if not exists async_migration_status (
    table_name          varchar(100),
    cursor_id           int,
    cursor_range        int8range,
    processed_timestamp timestamptz);"

  hasTable=$(targetTableExists "async_migration_status")
  if [[ "${hasTable}" = "t" ]]; then
    log "Skipping async_migration_status table creation since it already exists"
    return
  else
    log "Creating async_migration_status table"
    queryTarget "${tableDDL}"
  fi

  local conditions
  getCursorConditions conditions

  local cursorId=1
  local insertSql="\\copy async_migration_status(table_name, cursor_id, cursor_range) from program 'cat ${sqlTemp}' with csv delimiter ',';"
  local values=()
  for value in "${conditions[@]}"; do
    for table in $(echo "${ASYNC_TABLES[@]}" | tr "," "\n"); do
      values+=("${table},${cursorId},${value}")
    done
    cursorId=$((cursorId + 1))
  done
  IFS=$'\n'; echo "${values[*]}" > "${sqlTemp}"
  queryTarget "${insertSql}"
}

getCursorConditions() {
  local query="
  with record_file_stats as (
    select date_trunc('month', to_timestamp(consensus_end / 1000000000)) start_timestamp,
           sum(count) as record_count from record_file
    where consensus_end <= ${MAX_TIMESTAMP}
    group by 1 order by 1 desc
  ),
  total_records as (
    select sum(record_count) as total_record_count
    from record_file_stats
  )
  select
  100 * ((1.0 * rfs.record_count) / tr.total_record_count) as pct_of_total,
  (extract(epoch from rfs.start_timestamp) * 1000000000)::bigint as lower_bound,
  LEAST(${MAX_TIMESTAMP}, (extract(epoch from (rfs.start_timestamp + INTERVAL '1 month')) * 1000000000)::bigint) as upper_bound
  from record_file_stats rfs, total_records tr
  order by 3 desc
  "

  local upperBound=$MAX_TIMESTAMP
  local pctPerCursor=$(echo "scale=10; 100 / ${ASYNC_TABLE_SPLITS}" | bc)
  local totalPct=0
  local -n cursors=$1 # Init array to hold cursor definitions
  log "Will split data into ${pctPerCursor}% chunks"
  local stats=$(PGPASSWORD="${SOURCE_DB_PASSWORD}" psql -q --csv -t -h "${SOURCE_DB_HOST}" -d "${SOURCE_DB_NAME}" -p "${SOURCE_DB_PORT}" -U "${SOURCE_DB_USER}" -c "${query}")
  while IFS="," read -r pct_of_total lower_bound upper_bound; do
    upperBound=$((upper_bound > upperBound ? upper_bound : upperBound))
    totalPct=$(echo "(${totalPct} + ${pct_of_total})" | bc)
    local groupRange=$((upperBound - lower_bound))
    local rangeToTake=$(echo "scale=10; ${groupRange} / (${totalPct} / ${pctPerCursor})" | bc)
    while (($(echo "${totalPct} >= ${pctPerCursor}" | bc) )); do
      cursorLowerBound=$(echo "scale=0; (${upperBound} - ${rangeToTake}) / 1" | bc)
      if (( $(echo "${cursorLowerBound} < ${lower_bound}" | bc) )); then
        break
      fi
      totalPct=$(echo "${totalPct} - ${pctPerCursor}" | bc)
      cursors+=("\"(${cursorLowerBound},${upperBound}]\"")
      upperBound="${cursorLowerBound}"
    done
  done <<< "${stats}"

  if (( $(echo "${totalPct} > 0" | bc) )); then
    cursors+=("\"(,${upperBound}]\"")
  fi
}

migrateTableBinary() {
  local table="${1}"
  local columns="${2}"

  local has_data=$(queryTarget "select exists (select * from ${table}  limit 1)")
  if [[ "${has_data}" = "t" ]]; then
    log "Skipping '${table}' table since it contains existing data"
    return
  fi

  local where
  if [[ "${columns}" =~ $TS_COLUMN_REGEX ]]; then
    where="where consensus_timestamp <= ${MAX_TIMESTAMP}"

  # handle address book.
  elif [[ "${columns}" =~ \<consensus_timestamp_start\> ]]; then
    where="where consensus_timestamp_start <= ${MAX_TIMESTAMP}"
  fi

  queryTarget "$(getCopySql "${table}" "${columns}" "${where}")"
}

migrateTableAsyncBinary() {
  local table="${1}"
  local columns="${2}"
  local tmpDir="/tmp/migrate-${table}-$$"
  mkdir -m 700 "${tmpDir}" || die "Couldn't make safe tmp ${tmpDir}"
  pushd "${tmpDir}" || die "Couldn't change directory to tmp ${tmpDir}"

  if ! [[ "${columns}" =~ $TS_COLUMN_REGEX ]]; then
    log "ERROR: Table ${table} does not have a timestamp column and can not be migrated asynchronously."
    exit 1
  fi

  local cursorQuery="SELECT cursor_id,
                            lower(cursor_range),
                            upper(cursor_range),
                            lower_inc(cursor_range),
                            upper_inc(cursor_range)
                     FROM async_migration_status
                     WHERE table_name = '${table}' and
                           processed_timestamp IS NULL
                     ORDER BY upper(cursor_range) desc;"

  local tableCursors=$(PGPASSWORD="${TARGET_DB_PASSWORD}" psql -q --csv -t -h "${TARGET_DB_HOST}" -d "${TARGET_DB_NAME}" -p "${TARGET_DB_PORT}" -U "${TARGET_DB_USER}" -c "${cursorQuery}")

  while read -r line; do
    if [[ -z "${line}" ]]; then
      log "No more cursors to process for table ${table}"
      break
    fi

    IFS="," read -r cursor_id cursor_lower_bound cursor_upper_bound lower_inc upper_inc <<< "${line}"
    ACTIVE_COPIES=$(find . -maxdepth 1 -name "process-${table}*" -printf '.' | wc -m)
    while [[ "${ACTIVE_COPIES}" -ge ${CONCURRENT_COPIES_PER_TABLE} ]]; do
      sleep .5
      ACTIVE_COPIES=$(find . -maxdepth 1 -name "process-${table}*" -printf '.' | wc -m)
    done
    local lowerOperator
    local upperOperator

    if [[ "${lower_inc}" == "t" ]]; then
      lowerOperator=">="
    else
      lowerOperator=">"
    fi

    if [[ "${upper_inc}" == "t" ]]; then
      upperOperator="<="
    else
      upperOperator="<"
    fi

    local where
    if [[ -z "${cursor_lower_bound}" ]]; then
      where="where consensus_timestamp ${upperOperator} ${cursor_upper_bound}"
    else
      where="where consensus_timestamp ${upperOperator} ${cursor_upper_bound} and consensus_timestamp ${lowerOperator} ${cursor_lower_bound}"
    fi

    local processMarker="${tmpDir}/process-${table}-$(uuidgen)"
    touch "${processMarker}"

    local bookmarkSql="update async_migration_status set processed_timestamp=now() where table_name='${table}' and cursor_id=${cursor_id};"
    queryTarget "$(getCopySql "${table}" "${columns}" "${where}") ${bookmarkSql}" && rm "${processMarker}" &

    log "Starting to copy table ${table} with filter: ${where} on pid $!"
  done <<<"${tableCursors}"

  wait
  jobs
  popd || die "Couldn't change directory back to the original directory"
  rm -r "${tmpDir}"
}

migrateTable() {
  local table="${1}"

  trap "handlePipeError 'for table ${table} (BINARY)'" SIGPIPE
  trap "handleExit 'for table ${table} (BINARY)'" ERR

  local tableExists="f"
  tableExists=$(targetTableExists "${table}")
  if [[ "${tableExists}" = "f" ]]; then
    log "Skipping '${table}' table since it doesn't exist on the target"
    return
  fi

  local columns
  columns=$(getColumns "${table}")

  log "Starting to copy table '${table}'"

  if [[ "${ASYNC_TABLES}" =~ $table ]]; then
    migrateTableAsyncBinary "${table}" "${columns}"
  elif [[ "${table}" = "transaction_hash" ]]; then

    local selectColumns="th.consensus_timestamp, th.hash, t.payer_account_id"
    local join="th join transaction t on th.consensus_timestamp = t.consensus_timestamp"
    local where="where th.consensus_timestamp <= ${MAX_TIMESTAMP}"

    queryTarget "$(getCopySql "${table}" "${columns}" "${where}" "${join}" "${selectColumns}")"
  else
    migrateTableBinary "${table}" "${columns}"
  fi

  log "Copied '${table}' table in ${SECONDS}s"
}

insertRepeatableMigrations() {
  local migrationsSql="select description, type, script, checksum, installed_by, installed_on, execution_time, success
  from flyway_schema_history
  where version is null and
    upper(type)='JDBC' and
    script not ilike '%BackfillTransactionHashMigration%' and
    script not ilike '%TopicMessageLookupMigration%' and
    success
    order by installed_rank asc;"
  local currentRank=$(queryTarget "select max(installed_rank) from flyway_schema_history;")
  local migrations="$(PGPASSWORD="${SOURCE_DB_PASSWORD}" psql -q --csv -t -h "${SOURCE_DB_HOST}" -d "${SOURCE_DB_NAME}" -p "${SOURCE_DB_PORT}" -U "${SOURCE_DB_USER}" -c "${migrationsSql}")"
  local rows=()

  while read -r line; do
    local description="$(echo "${line}" | csvcut -c 1)"
    local type="$(echo "${line}" | csvcut -c 2)"
    local script="$(echo "${line}" | csvcut -c 3)"
    local checksum="$(echo "${line}" | csvcut -c 4)"
    local installed_by="$(echo "${line}" | csvcut -c 5)"
    local installed_on="$(echo "${line}" | csvcut -c 6)"
    local execution_time="$(echo "${line}" | csvcut -c 7)"

    local existsOnTarget=$(queryTarget "select exists (select from flyway_schema_history where script = '${script}' and checksum=${checksum});")
    if [[ "${existsOnTarget}" != "t" ]]; then
      currentRank=$((currentRank + 1))
      rows+=("${currentRank},${description},${type},${script},${checksum},${installed_by},${installed_on},${execution_time},true")
    else
      log "Skipping migration ${script} with checksum ${checksum} since it already exists on the target"
    fi
  done <<< "${migrations}"

  if [[ "${#rows[@]}" -eq 0 ]]; then
    log "No repeatable migrations to insert"
    return
  fi

  local sqlTemp="/tmp/migrations-$$.csv"
  local insertSql="\\copy flyway_schema_history(installed_rank, description, type, script, checksum, installed_by, installed_on, execution_time, success) from program 'cat ${sqlTemp}' with csv delimiter ',';"
  IFS=$'\n'; echo "${rows[*]}" > "${sqlTemp}"
  queryTarget "${insertSql}"
}

removeRepeatableMigrations() {
  local sql="with last as (
    select installed_rank as rank
    from flyway_schema_history
    where version is null and
      upper(type)='JDBC' and
      script ilike '%BackfillTransactionHashMigration%'
    order by installed_rank desc
    limit 1
  )
  delete from flyway_schema_history
  using last
  where installed_rank = rank and success;"
  queryTarget "${sql}"
}

# Export the functions so they can be invoked via parallel xargs
export -f log querySource queryTarget migrateTable migrateTableBinary migrateTableAsyncBinary handlePipeError handleExit targetTableExists getColumns getCopySql

SECONDS=0
SCRIPTS_DIR="$(pwd)"
FLYWAY_DIR="/tmp/flyway"
MIGRATIONS_DIR="${SCRIPTS_DIR}/../../migration/v2"
export PATH="${FLYWAY_DIR}/:$PATH"
source "${SCRIPTS_DIR}/migration.config"

export ASYNC_TABLE_SPLITS=${ASYNC_TABLE_SPLITS:-1000}
export CONCURRENCY=${CONCURRENCY:-5}
export CONCURRENT_COPIES_PER_TABLE=${CONCURRENT_COPIES_PER_TABLE:-5}
export MAX_TIMESTAMP="${MAX_TIMESTAMP:-$(getMaxTimestamp)}"
export SOURCE_DB_HOST="${SOURCE_DB_HOST:-127.0.0.1}"
export SOURCE_DB_NAME="${SOURCE_DB_NAME:-mirror_node}"
export SOURCE_DB_PASSWORD="${SOURCE_DB_PASSWORD:-mirror_node_pass}"
export SOURCE_DB_PORT="${SOURCE_DB_PORT:-5432}"
export SOURCE_DB_SCHEMA="${SOURCE_DB_SCHEMA:-public}"
export SOURCE_DB_USER="${SOURCE_DB_USER:-mirror_node}"

export TARGET_DB_HOST="${TARGET_DB_HOST:-127.0.0.1}"
export TARGET_DB_NAME="${TARGET_DB_NAME:-mirror_node}"
export TARGET_DB_PASSWORD="${TARGET_DB_PASSWORD:-mirror_node_pass}"
export TARGET_DB_PORT="${TARGET_DB_PORT:-5432}"
export TARGET_DB_SCHEMA="${TARGET_DB_SCHEMA:-public}"
export TARGET_DB_USER="${TARGET_DB_USER:-mirror_node}"
export TS_COLUMN_REGEX="\<consensus_timestamp\>"

SOURCE_QUERY="
select exists
(
  select from information_schema.tables
  where table_name = 'flyway_schema_history' and table_schema = '${SOURCE_DB_SCHEMA}'
);"

SOURCE_DB_HAS_DATA=$(querySource "${SOURCE_QUERY}")
if [[ "${SOURCE_DB_HAS_DATA}" != "t" ]]; then
  echo "Unable to verify the state of the source database. Either the connection information is wrong or the data is missing."
  exit 1
fi

TARGET_DB_HEALTHY=$(queryTarget "select exists (select from citus_check_cluster_node_health() where result = true);")
if [[ "${TARGET_DB_HEALTHY}" != "t" ]]; then
  echo "Unable to verify the state of the target database."
  exit 1
fi

rm -rf "${FLYWAY_DIR}"
mkdir -p "${FLYWAY_DIR}" || die "Couldn't create directory ${FLYWAY_DIR}"
pushd "${FLYWAY_DIR}" || die "Couldn't change directory to ${FLYWAY_DIR}"

log "Installing Flyway"
wget -qO- "${FLYWAY_URL}" | tar -xz && mv flyway-*/* .
log "Flyway installed"

cp "${MIGRATIONS_DIR}/V2.0."* "${FLYWAY_DIR}"/sql/

log "Copying Flyway configuration"
cat >"${FLYWAY_DIR}/conf/flyway.conf" <<EOF
flyway.password=${TARGET_DB_PASSWORD}
flyway.placeholders.hashShardCount=6
flyway.placeholders.maxEntityId=5000000
flyway.placeholders.maxEntityIdRatio=2.0
flyway.placeholders.partitionStartDate='2019-09-01'
flyway.placeholders.partitionTimeInterval='1 month'
flyway.placeholders.schema=${TARGET_DB_SCHEMA}
flyway.placeholders.shardCount=16
flyway.url=jdbc:postgresql://${TARGET_DB_HOST}:${TARGET_DB_PORT}/${TARGET_DB_NAME}
flyway.user=${TARGET_DB_USER}
EOF

log "Running Flyway migrate"
flyway migrate

TABLES_QUERY="
select pc.relname from pg_class pc
join pg_database pd on pc.relowner = pd.datdba
join pg_catalog.pg_namespace ns on ns.oid = pc.relnamespace
left join pg_catalog.pg_statio_user_tables psu on psu.relname = pc.relname
where ns.nspname='${SOURCE_DB_SCHEMA}' and pd.datname = '${SOURCE_DB_NAME}' and pc.relkind in ('p', 'r') and pc.relname not similar to '%[0-9]' and pc.relname not in (${EXCLUDED_TABLES})
and pc.relname not in (${ASYNC_TABLES})
order by pg_total_relation_size(psu.relid) asc NULLS LAST;"

TABLES=$(querySource "${TABLES_QUERY}" | tr " " "\n")
ASYNC_TABLES=$(echo "${ASYNC_TABLES//[\']/}" | tr "," "\n")

declare tablesArray
IFS=" " readarray -t tablesArray <<< "${TABLES}"
tablesArray+=("${ASYNC_TABLES[@]}")
COUNT="${#tablesArray[@]}"

insertCursors

log "Migrating ${COUNT} tables from ${SOURCE_DB_HOST}:${SOURCE_DB_PORT} to ${TARGET_DB_HOST}:${TARGET_DB_PORT}. With max consensus_timestamp ${MAX_TIMESTAMP} and Tables: ${tablesArray[*]}"
echo "${tablesArray[*]}" | tr " " "\n" |ASYNC_TABLES=$ASYNC_TABLES xargs -n 1 -P "${CONCURRENCY}" -I {} bash -c 'migrateTable "$@"' _ {}

log "inserting repeatable migrations"
insertRepeatableMigrations

log "removing repeatable migrations"
removeRepeatableMigrations

log "migration completed in $SECONDS seconds."
popd || die "Couldn't change directory back to ${SCRIPTS_DIR}"
exit 0
