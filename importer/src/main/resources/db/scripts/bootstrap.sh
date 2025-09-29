#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0

kernel_name=$(uname -s)
if [[ "$kernel_name" != "Linux" ]]; then
  echo "[ERROR] This script is designed to run only on Linux. Detected OS: $kernel_name. Exiting." >&2
  exit 1
fi

exec 9>&2

set -m # Start a new process group and detach from terminal
exec 1>/dev/null 2>>bootstrap.log
[[ -t 1 ]] && exec </dev/null >&0 2>&0

exec 3>&2
exec 2>/dev/null

SCRIPT_TEMP_DIR="./temp"
SCHEMA="public"

rm -rf "$SCRIPT_TEMP_DIR"
mkdir -p "$SCRIPT_TEMP_DIR"

PID_FILE="$SCRIPT_TEMP_DIR/bootstrap.pid"
script_name=$(basename "$0")

# Check if another instance of the script is already running
if [[ -f "$PID_FILE" ]]; then
  old_pid=$(cat "$PID_FILE" 2>/dev/null)
  if [[ -n "$old_pid" && "$old_pid" =~ ^[0-9]+$ ]]; then
    if ps -p "$old_pid" -o comm= | grep -q -E "^(bash|sh|$script_name|${script_name%.sh})$"; then
      if [[ -f "/proc/$old_pid/cmdline" ]] && grep -q "$script_name" "/proc/$old_pid/cmdline"; then
        echo "[ERROR] Another instance of $script_name is already running with PID: $old_pid. Exiting." >&9
        exit 1
      else
        echo "[WARN] Stale PID file found ($PID_FILE) for PID $old_pid, but it's not a $script_name process. Removing stale file." >&9
        rm -f "$PID_FILE"
      fi
    else
      echo "[WARN] Stale PID file found ($PID_FILE) for non-existent process PID $old_pid. Removing stale file." >&9
      rm -f "$PID_FILE"
    fi
  else
    echo "[WARN] Stale PID file found ($PID_FILE) but contains invalid PID '$old_pid'. Removing stale file." >&9
    rm -f "$PID_FILE"
  fi
fi

# Write script's PID to $PID_FILE for process management
echo $$ > "$PID_FILE"

####################################
# Variables
####################################

REQUIRED_BASH_MAJOR=4
REQUIRED_BASH_MINOR=3

DEBUG_MODE=${DEBUG_MODE:-false}

LOG_FILE="bootstrap.log"
TRACKING_FILE="bootstrap_tracking.txt"
DISCREPANCY_FILE="bootstrap_discrepancies.log"
PROGRESS_FILE="bootstrap_progress.log"
PROGRESS_INTERVAL=${PROGRESS_INTERVAL:-10}
PROGRESS_DB_TABLE="bootstrap_manifest_progress"

LOG_FILE_LOCK="$SCRIPT_TEMP_DIR/$LOG_FILE.lock"
TRACKING_FILE_TMP="$SCRIPT_TEMP_DIR/bootstrap_tracking.tmp"
TRACKING_LOCK_FILE="$SCRIPT_TEMP_DIR/bootstrap_tracking.lock"
DISCREPANCY_FILE_LOCK="$SCRIPT_TEMP_DIR/$DISCREPANCY_FILE.lock"

MONITOR_TEMP_DIR="$SCRIPT_TEMP_DIR/monitor"
mkdir -p "$MONITOR_TEMP_DIR"
export MONITOR_STATE_FILE="$MONITOR_TEMP_DIR/bootstrap_monitor_state.txt"
MONITOR_STOP_SIGNAL_FILE="$MONITOR_TEMP_DIR/bootstrap_monitor.stop"

# Required system tools
# Note: A decompressor (rapidgzip, igzip, or gunzip) is checked separately
REQUIRED_TOOLS=("awk" "b3sum" "basename" "bc" "cat" "chmod" "column" "curl" "date" "dd" "find" "flock"
    "grep" "head" "mkdir" "mkfifo" "mktemp" "mv" "nproc" "pgrep" "pkill" "ps" "psql" "python3"
    "realpath" "rm" "sed" "sleep" "sort" "stat" "tail" "touch" "tr" "wc")

export DECOMPRESS_TOOL=""
export DECOMPRESS_FLAGS=""
export DECOMPRESSOR_CHECKED=false
MISSING_TOOLS=()

export DB_SKIP_FLAG_FILE="SKIP_DB_INIT"
export CLEANUP_IN_PROGRESS_FILE="$SCRIPT_TEMP_DIR/BOOTSTRAP_CLEANUP"

USE_FULL_DB=""
MANIFEST_FILE=""

# Parallel processing configuration
B3SUM_NUM_THREADS=${B3SUM_THREADS:-2}               # Number of threads for BLAKE3 hash calculation
DECOMPRESSOR_NUM_THREADS=${DECOMPRESSOR_THREADS:-2} # Number of threads for the selected decompressor (rapidgzip or igzip)
MAX_JOBS=""                                         # Maximum number of concurrent import jobs

# Associative array for manifest row counts
declare -A manifest_counts
# Process tracking array
declare -a pids=()

####################################
# Functions
####################################

enable_pipefail() {
  set -euo pipefail
}

# shellcheck disable=SC2317
disable_pipefail() {
  set +euo pipefail
}

export -f enable_pipefail disable_pipefail

with_lock() {
  local lockfile="$1"
  local cmd="$2"

  (
    flock -x 200
    eval "$cmd"
  ) 200>"$lockfile"
}

export -f with_lock

log() {
  local msg="$1"
  local level="${2:-INFO}"

  # Print debug messages only when DEBUG_MODE="true"
  if [[ "$DEBUG_MODE" == "false" && "$level" == "DEBUG" ]]; then
    return
  fi

  # During cleanup, only log messages from cleanup itself
  if [[ -f "$CLEANUP_IN_PROGRESS_FILE" && "$level" != "TERMINATE" ]]; then
    return
  fi

  local timestamp
  timestamp=$(date -u '+%Y-%b-%d %H:%M:%S')
  local log_cmd="echo \"[$timestamp] [$level] $msg\" >> \"$LOG_FILE\""

  with_lock "$LOG_FILE_LOCK" "$log_cmd"
}

show_help() {
  cat > /dev/tty << EOF
Usage: $0 DB_CPU_CORES [--full] IMPORT_DIR

Imports data into a PostgreSQL database from compressed CSV files.

Options:
  -h, --help, -H     Show this help message and exit.
  --full             Use full database manifest (manifest.csv), otherwise default to minimal (manifest.minimal.csv)

Arguments:
  DB_CPU_CORES       Number of CPU cores on the DB instance to thread the import jobs.
  IMPORT_DIR         Directory containing the compressed CSV files and manifests.

Example:
  # Import minimal database (using manifest.minimal.csv)
  $0 8 /path/to/data

  # Import full database (using manifest.csv)
  $0 8 --full /path/to/data
EOF
}

check_bash_version() {
  local current_major=${BASH_VERSINFO[0]}
  local current_minor=${BASH_VERSINFO[1]}

  if (( current_major < REQUIRED_BASH_MAJOR )) || \
    (( current_major == REQUIRED_BASH_MAJOR && current_minor < REQUIRED_BASH_MINOR )); then
    log "Bash version ${REQUIRED_BASH_MAJOR}.${REQUIRED_BASH_MINOR}+ is required. Current version is ${BASH_VERSION}." "ERROR"
    exit 1
  fi
}

check_required_tools() {
  for tool in "${REQUIRED_TOOLS[@]}"; do
    if ! command -v "$tool" &> /dev/null; then
        MISSING_TOOLS+=("$tool")
    fi
  done

  if ! $DECOMPRESSOR_CHECKED; then
    if ! determine_decompression_tool; then
      log "No decompression tool found - at minimum gunzip is required" "ERROR"
      log "Recommended tools for faster decompression:" "ERROR"
      log "  - rapidgzip (fastest): https://github.com/mxmlnkn/rapidgzip" "ERROR"
      log "  - igzip (next best): https://github.com/intel/isa-l" "ERROR"
    fi
    DECOMPRESSOR_CHECKED=true
  fi

  if [ "${#MISSING_TOOLS[@]}" -gt 0 ]; then
    log "The following required tools are not installed:" "ERROR"
    printf "%s\n" "${MISSING_TOOLS[@]}" | sort -u | while read -r tool; do
      log "  - $tool" "ERROR"
    done
    log "Please install them to continue." "ERROR"
    return 1
  fi

  return 0
}

determine_decompression_tool() {
  if command -v rapidgzip >/dev/null 2>&1; then
    DECOMPRESS_TOOL="rapidgzip"
    DECOMPRESS_FLAGS=(-d -c "-P${DECOMPRESSOR_NUM_THREADS}")
    log "Using rapidgzip with ${DECOMPRESSOR_NUM_THREADS} threads for decompression"
    return 0
  fi

  if command -v igzip >/dev/null 2>&1; then
    DECOMPRESS_TOOL="igzip"
    DECOMPRESS_FLAGS=(-d -c "-T${DECOMPRESSOR_NUM_THREADS}")
    log "Using igzip with ${DECOMPRESSOR_NUM_THREADS} threads for decompression"
    return 0
  fi

  if command -v gunzip >/dev/null 2>&1; then
    DECOMPRESS_TOOL="gunzip"
    DECOMPRESS_FLAGS=(-c)
    log "Using gunzip for decompression"
    return 0
  fi

  MISSING_TOOLS+=("gunzip")
  return 1
}

# shellcheck disable=SC2317
kill_descendants() {
  local pid="$1"
  local children
  children=$(pgrep -P "$pid" 2>/dev/null)
  for child in $children; do
    kill_descendants "$child"
  done
  kill -9 "$pid" >/dev/null 2>&1
}

# shellcheck disable=SC2317
cleanup() {
  disable_pipefail
  local trap_type="$1"
  local exit_code=1

  log "Cleanup function triggered with trap type: $trap_type"

  if [[ "$trap_type" == "INT" || "$trap_type" == "TERM" || "$trap_type" == "PIPE" ]]; then
    exec 2>/dev/null
    touch "$CLEANUP_IN_PROGRESS_FILE"

    if [[ -f "$TRACKING_FILE_TMP" ]]; then
      mv "$TRACKING_FILE_TMP" "$TRACKING_FILE"
    fi

    pkill -9 psql 2>/dev/null || true

    if [[ -n "$DECOMPRESS_TOOL" ]]; then
      pkill -9 "$DECOMPRESS_TOOL" 2>/dev/null || true
    fi

    for pid in "${pids[@]}"; do
      if kill -0 "$pid" 2>/dev/null; then
        kill_descendants "$pid"
      fi
    done

    pkill -9 -P $$ 2>/dev/null || true
    log "Script interrupted. All processes terminated." "TERMINATE"

    wait 2>/dev/null || true
  fi

  if command -v psql >/dev/null 2>&1; then
    log "Attempting final cleanup of temporary progress table..." "DEBUG"
    psql -q -c "DROP TABLE IF EXISTS ${PROGRESS_DB_TABLE};" >/dev/null 2>&1 || true
  fi

  rm -f "$PID_FILE" "$CLEANUP_IN_PROGRESS_FILE" "$PROGRESS_FILE" || true

  if [[ "$trap_type" != "INT" && "$trap_type" != "TERM" && "$trap_type" != "PIPE" ]]; then
      if [[ -v overall_success ]] && [[ "$overall_success" == true ]]; then
          exit_code=0
      else
          exit_code=1
      fi
  fi

  if [[ "$trap_type" != "EXIT" ]]; then
    exit "$exit_code"
  fi
}

write_tracking_file() {
  local file="$1"
  local new_status="${2:-}"
  local new_hash_status="${3:-}"
  local basename_file
  basename_file=$(basename "$file")

  local cmd="
    # Get the full existing line
    local full_existing_line
    full_existing_line=\$(grep \"^$basename_file \" \"$TRACKING_FILE\" 2>/dev/null | tail -n 1)

    local old_status=''
    local old_hash=''
    if [[ -n \"\$full_existing_line\" ]]; then
      old_status=\$(echo \"\$full_existing_line\" | awk '{print \$2}')
      old_hash=\$(echo \"\$full_existing_line\" | awk '{print \$3}')
    fi

    # If a line exists but no new import status was passed, keep the old import status
    if [[ -z \"$new_status\" ]]; then
      if [[ -n \"\$old_status\" ]]; then
        new_status=\"\$old_status\"
      else
        new_status=\"NOT_STARTED\"
      fi
    fi

    # If a line exists but no new hash status was passed, keep the old hash status
    if [[ -z \"$new_hash_status\" ]]; then
      if [[ -n \"\$old_hash\" ]]; then
        new_hash_status=\"\$old_hash\"
      else
        new_hash_status=\"HASH_UNVERIFIED\"
      fi
    fi

    # Remove any existing entry for this file
    grep -v \"^$basename_file \" \"$TRACKING_FILE\" > \"$TRACKING_FILE_TMP\" 2>/dev/null || true
    mv \"$TRACKING_FILE_TMP\" \"$TRACKING_FILE\"

    # Add the updated line with the final status and hash
    echo \"$basename_file \$new_status \$new_hash_status\" >> \"$TRACKING_FILE\"
  "

  with_lock "$TRACKING_LOCK_FILE" "$cmd"
}

read_tracking_status() {
  local file="$1"
  local basename_file
  basename_file=$(basename "$file")
  grep "^$basename_file " "$TRACKING_FILE" 2>/dev/null | tail -n 1 | awk '{print $2}' | tr -d '[:space:]'
}

collect_import_tasks() {
  enable_pipefail
  trap 'disable_pipefail' RETURN

  find "$IMPORT_DIR" -type f -name "*.csv.gz"
}

# shellcheck disable=SC2317
write_discrepancy() {
  local file="$1"
  local expected_count="$2"
  local actual_count="$3"

  if [[ "$(read_tracking_status "$file")" != "IMPORTED" ]] && [[ ! -f "$CLEANUP_IN_PROGRESS_FILE" ]]; then
    local discrepancy_entry="$file: expected $expected_count, got $actual_count rows"
    local cmd="echo \"$discrepancy_entry\" >> \"$DISCREPANCY_FILE\""
    with_lock "$DISCREPANCY_FILE_LOCK" "$cmd"
  fi
}

find_full_path() {
  local basename_file="$1"
  local result
  result=$(find "$IMPORT_DIR" -type f -name "$basename_file" | head -1)

  echo "$result"
}

# Load PostgreSQL connection settings from $BOOTSTRAP_ENV_FILE
# Required vars: PGUSER, PGPASSWORD, PGDATABASE, PGHOST, PGPORT
source_bootstrap_env() {
  local BOOTSTRAP_ENV_FILE="bootstrap.env"

  if [[ -f "$BOOTSTRAP_ENV_FILE" ]]; then
    log "Sourcing $BOOTSTRAP_ENV_FILE to set environment variables."
    set -a
    # shellcheck disable=SC1090
    source "$BOOTSTRAP_ENV_FILE"
    set +a
  else
    log "$BOOTSTRAP_ENV_FILE file not found." "ERROR"
    exit 1
  fi
}

load_manifest_to_db() {
  enable_pipefail
  trap 'disable_pipefail' RETURN

  log "Loading manifest counts into temporary DB table ${PROGRESS_DB_TABLE}..."

  if [[ "$PGUSER" != "mirror_node" || "$PGDATABASE" != "mirror_node" ]]; then
    log "load_manifest_to_db must run as mirror_node user in mirror_node DB. Current: $PGUSER@$PGDATABASE" "ERROR"
    return 1
  fi

  psql -v ON_ERROR_STOP=1 -q -c "DROP TABLE IF EXISTS ${PROGRESS_DB_TABLE};" || {
    log "Failed to drop existing ${PROGRESS_DB_TABLE} table." "ERROR"
    return 1
  }

  # Create a temporary, unlogged table for progress tracking
  psql -v ON_ERROR_STOP=1 -q -c """
  CREATE UNLOGGED TABLE ${PROGRESS_DB_TABLE} (
    filename TEXT PRIMARY KEY,
    expected_count BIGINT
  );
  """ || {
    log "Failed to create ${PROGRESS_DB_TABLE} table." "ERROR"
    return 1
  }

  local sql_temp_dir="$SCRIPT_TEMP_DIR/sql"
  mkdir -p "$sql_temp_dir"
  local insert_sql_file
  insert_sql_file=$(mktemp "$sql_temp_dir/insert_sql.XXXXXX")
  echo "BEGIN;" > "$insert_sql_file"

  local filename count status
  local inserted_count=0
  log "Filtering manifest entries for non-IMPORTED files..." "DEBUG"
  for filename in "${!manifest_counts[@]}"; do
    count="${manifest_counts[$filename]}"

    status=$(read_tracking_status "$filename")

    if [[ "$status" != "IMPORTED" && "$count" =~ ^[0-9]+$ ]]; then
      safe_filename=$(echo "$filename" | sed "s/'/''/g")
      echo "INSERT INTO ${PROGRESS_DB_TABLE} (filename, expected_count) VALUES ('${safe_filename}', ${count});" >> "$insert_sql_file"
      ((inserted_count++))
    else
      log "Skipping manifest entry for $filename (status: $status, count: $count)" "DEBUG"
    fi
  done

  echo "COMMIT;" >> "$insert_sql_file"

  if ! psql -v ON_ERROR_STOP=1 -q -f "$insert_sql_file"; then
    log "Failed to load data into ${PROGRESS_DB_TABLE} table via INSERT statements." "ERROR"
    psql -q -c "DROP TABLE IF EXISTS ${PROGRESS_DB_TABLE};" >/dev/null 2>&1
    rm -f "$insert_sql_file"
    return 1
  fi

  rm -f "$insert_sql_file"

  log "Successfully loaded ${inserted_count} manifest entries into ${PROGRESS_DB_TABLE}."

  local table_exists
  table_exists=$(psql -X -q -t -c "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_schema = '${SCHEMA}' and table_name = '${PROGRESS_DB_TABLE}');" | tr -d ' ')
  log "After load_manifest_to_db, table exists: $table_exists" "DEBUG"

  if [[ "$table_exists" == "t" ]]; then
    local row_count
    row_count=$(psql -X -q -t -c "SELECT COUNT(*) FROM ${PROGRESS_DB_TABLE};" | tr -d ' ')
    log "Table ${PROGRESS_DB_TABLE} has ${row_count} rows" "DEBUG"

    if [[ "$row_count" -gt 0 ]]; then
      log "Sample row from ${PROGRESS_DB_TABLE}:" "DEBUG"
      psql -X -q -t -c "SELECT * FROM ${PROGRESS_DB_TABLE} LIMIT 1;" || log "Failed to retrieve sample row" "DEBUG"
    fi
  fi

  return 0
}

process_manifest() {
  enable_pipefail
  trap 'disable_pipefail' RETURN

  # Declare manifest_counts and manifest_tables as global associative arrays
  declare -g -A manifest_sizes
  declare -g -A manifest_hashes
  declare -g -A manifest_counts
  declare -g -A manifest_tables
  declare -a missing_files=()

  log "Using $B3SUM_NUM_THREADS thread(s) per BLAKE3 process."

  if [[ ! -f "$MANIFEST_FILE" ]]; then
    log "Manifest file '$MANIFEST_FILE' not found." "ERROR"
    exit 1
  fi

  log "Validating file count"
  manifest_file_count=$(awk 'NR>1' "$MANIFEST_FILE" | wc -l)

  actual_file_count=$(find "$IMPORT_DIR" -type f -name "*.gz" | wc -l)

  if [[ "$manifest_file_count" != "$actual_file_count" ]]; then
    log "File count mismatch! Manifest: $manifest_file_count, Directory: $actual_file_count" "ERROR"
    exit 1
  fi
  log "File count validation successful, Manifest: $manifest_file_count, Directory: $actual_file_count"

  manifest_content=$(tail -n +2 "$MANIFEST_FILE" 2>/dev/null)
  if [[ -z "$manifest_content" ]]; then
    log "Failed to read manifest content with tail" "ERROR"
    return 1
  fi

  while IFS=',' read -r filename expected_count expected_size expected_blake3_hash; do
    if [[ "$filename" == "filename" ]]; then
      continue
    fi

    file_path="$IMPORT_DIR/$filename"

    if [[ -f "$file_path" ]]; then
      if [[ "$(read_tracking_status "$file_path")" == "IMPORTED" ]]; then
        continue
      fi

      if [[ "$expected_count" != "N/A" ]]; then
        manifest_counts["$filename"]="$expected_count"

        table=$(get_table_name_from_filename "$filename")
        if [[ -z "$table" ]]; then
          log "Could not determine table name from filename: $filename" "ERROR"
          continue
        fi

        manifest_tables["$table"]=1
      fi
      manifest_sizes["$filename"]="$expected_size"
      manifest_hashes["$filename"]="$expected_blake3_hash"
    else
      missing_files+=("$filename")
    fi
  done < <(tr -d '\r' < "$MANIFEST_FILE")

  MANIFEST_SIZES_SERIALIZED=$(declare -p manifest_sizes)
  MANIFEST_HASHES_SERIALIZED=$(declare -p manifest_hashes)
  export MANIFEST_SIZES_SERIALIZED MANIFEST_HASHES_SERIALIZED

  if [[ ${#missing_files[@]} -gt 0 ]]; then
    log "The following files are listed in the manifest but are missing from the data directory:" "ERROR"
    for missing_file in "${missing_files[@]}"; do
      log "  - $missing_file" "ERROR"
    done
    exit 1
  fi

  if [[ -f "$TRACKING_LOCK_FILE" ]]; then
    rm -f "$TRACKING_LOCK_FILE"
  fi

  if [[ "$PGUSER" == "mirror_node" && "$PGDATABASE" == "mirror_node" ]]; then
      psql -q -c "DROP TABLE IF EXISTS ${PROGRESS_DB_TABLE};" >/dev/null 2>&1 || true
  fi
}

get_table_name_from_filename() {
  local filename="$1"
  local basename_file
  basename_file=$(basename "$filename")

  local name="${basename_file%.csv.gz}"
  if [[ "$name" =~ ^(.*)_p[0-9]{4}_[0-9]{2}(_atma)?$ ]]; then
    echo "${BASH_REMATCH[1]}"
  else
    echo "$name"
  fi
}

validate_file() {
  local file="$1"
  local filename="$2"

  if [[ ! -f "$file" ]]; then
    log "Missing required file: $file" "ERROR"
    return 1
  fi

  actual_size=$(stat -c%s "$file")
  expected_size="${manifest_sizes["$filename"]}"

  if [[ "$actual_size" != "$expected_size" ]]; then
    log "SIZE_MISMATCH for $file: Expected $expected_size bytes, Actual $actual_size bytes" "ERROR"
    return 1
  fi

  log "Starting BLAKE3 hash calculation for $file..." "DEBUG"
  actual_b3sum=$(b3sum --num-threads "$B3SUM_NUM_THREADS" --no-names "$file")
  log "Finished BLAKE3 hash calculation for $file." "DEBUG"

  expected_blake3_hash="${manifest_hashes["$filename"]}"

  if [[ "$actual_b3sum" != "$expected_blake3_hash" ]]; then
    log "HASH_MISMATCH for $file: Expected $expected_blake3_hash, Actual $actual_b3sum" "ERROR"
    return 1
  fi

  return 0
}

validate_special_files() {
  log "Entering validate_special_files" "DEBUG"
  enable_pipefail
  trap 'disable_pipefail' RETURN

  local special_files=("schema.sql.gz" "MIRRORNODE_VERSION.gz")
  local validation_failed=false
  local failures=()

  for filename in "${special_files[@]}"; do
    local file="$IMPORT_DIR/$filename"
    if [[ "$(read_tracking_status "$filename")" == "IMPORTED" ]]; then
      log "Special file '$filename' already verified. Skipping."
      continue
    fi

    if ! validate_file "$file" "$filename"; then
      failures+=("$filename")
      validation_failed=true
      write_tracking_file "$filename" "FAILED_VALIDATION"
    else
      log "Successfully validated special file: $filename"
      write_tracking_file "$filename" "IMPORTED" "HASH_VERIFIED"
    fi
  done

  if [[ "$validation_failed" == "true" ]]; then
    log "Special file validation failed:" "ERROR"
    for failure in "${failures[@]}"; do
      log "  - Failed validation for: $failure" "ERROR"
    done
    return 1
  fi

  return 0
}

# shellcheck disable=SC2317
retry_query() {
  local query="$1"
  local retries=3
  local delay=2
  local result
  local status
  local current_pid=$BASHPID

  result=$(PGAPPNAME="bootstrap_$current_pid" psql -v ON_ERROR_STOP=1 -q -Atc "$query")
  status=$?

  for ((i=1; i<retries; i++)); do
    if [ "$status" -eq 0 ]; then
      break
    fi

    sleep "$delay"
    delay=$((delay * 2))
    result=$(PGAPPNAME="bootstrap_$current_pid" psql -v ON_ERROR_STOP=1 -q -Atc "$query")
    status=$?
  done

  echo "${result}:${status}"
}

verify_postgres_connection() {
  log "Verifying PostgreSQL connection..."

  if ! psql -c "SELECT version();" > /dev/null 2>&1; then
    log "Failed to connect to PostgreSQL database!" "ERROR"
    log "Connection details: host=$PGHOST port=$PGPORT dbname=$PGDATABASE user=$PGUSER" "ERROR"
    return 1
  fi

  return 0
}

import_file() {
  if [[ $# -ne 1 ]]; then
    log "Usage: import_file filename" "ERROR"
    return 1
  fi

  if [[ -f "$CLEANUP_IN_PROGRESS_FILE" ]]; then
    return 1
  fi

  enable_pipefail
  trap 'disable_pipefail' RETURN

  local filename="$1"
  local table
  local absolute_file
  local file
  file=$(basename "$filename")
  local relative_path
  local current_pid=$BASHPID

  export PGAPPNAME="bootstrap_copy_$file"
  export PGOPTIONS="--client-min-messages=warning"

  # Reconstruct associative arrays from serialized data
  declare -A manifest_counts
  declare -A manifest_sizes
  declare -A manifest_hashes
  eval "$MANIFEST_COUNTS_SERIALIZED"
  eval "$MANIFEST_SIZES_SERIALIZED"
  eval "$MANIFEST_HASHES_SERIALIZED"

  if [[ "$filename" != /* && ! -f "$filename" ]]; then
    absolute_file=$(find_full_path "$(basename "$filename")")
  else
    absolute_file=$(realpath "$filename")
  fi

  file=$(basename "$absolute_file")
  export PGAPPNAME="bootstrap_copy_$file"

  if [[ -z "$absolute_file" || ! -f "$absolute_file" ]]; then
    log "Invalid file path or file not found: $file" "ERROR"
    return 1
  fi

  if [[ "$absolute_file" == "$IMPORT_DIR"* ]]; then
    relative_path=${absolute_file#"$IMPORT_DIR/"}
  else
    relative_path="$file"
  fi

  expected_count="${manifest_counts[$relative_path]}"

  # Perform BLAKE3 and file-size validations
  local validation_result
  if ! validation_result=$(validate_file "$absolute_file" "$relative_path"); then
    if [[ ! -f "$CLEANUP_IN_PROGRESS_FILE" ]]; then
      log "Validation failed for $file: $validation_result" "ERROR"
    fi
    write_tracking_file "$filename" "FAILED_VALIDATION"
    ((failed_imports++))
    return 1
  fi

  log "Successfully validated file-size and BLAKE3 hash for $file"
  write_tracking_file "$filename" "NOT_STARTED" "HASH_VERIFIED"

  # Skip non-table/special files after validation
  if [[ "$filename" == "MIRRORNODE_VERSION.gz" || "$filename" == "schema.sql.gz" ]]; then
    log "Successfully validated non-table file: $file"
    write_tracking_file "$filename" "IMPORTED" "HASH_VERIFIED"
    return 0
  fi

  table=$(get_table_name_from_filename "$filename")
  if [[ -z "$table" ]]; then
    log "Could not determine table name from filename: $file, skipping import" "ERROR"
    return 1
  fi

  log "Importing into table $table from $filename, PID: $current_pid"
  write_tracking_file "$filename" "IN_PROGRESS"

  local job_temp_dir="$SCRIPT_TEMP_DIR/import-jobs/$current_pid"
  mkdir -p "$job_temp_dir" || { log "Failed to create job temp directory $job_temp_dir" "ERROR"; return 1; }

  local stdout_file
  stdout_file=$(mktemp "$job_temp_dir/stdout.XXXXXX") || { log "mktemp failed for stdout_file: $?" "ERROR"; return 1; }
  local stderr_file
  stderr_file=$(mktemp "$job_temp_dir/stderr.XXXXXX") || { log "mktemp failed for stderr_file: $?" "ERROR"; return 1; }
  local decomp_err_file
  decomp_err_file=$(mktemp "$job_temp_dir/decomp_err.XXXXXX") || { log "mktemp failed for decomp_err_file: $?" "ERROR"; return 1; }
  local row_counter
  row_counter=$(mktemp "$job_temp_dir/row_count.XXXXXX") || { log "mktemp failed for row_counter: $?" "ERROR"; return 1; }
  local header_stderr_file
  header_stderr_file=$(mktemp "$job_temp_dir/header_stderr.XXXXXX") || { log "mktemp failed for header_stderr_file: $?" "ERROR"; return 1; }
  log "Created header_stderr_file: $header_stderr_file" "DEBUG"
  local python_stderr_file
  python_stderr_file=$(mktemp "$job_temp_dir/python_header_stderr.XXXXXX") || { log "mktemp failed for python_stderr_file: $?" "ERROR"; return 1; }

  # Create a named pipe for data transfer
  local csv_fifo
  csv_fifo=$(mktemp -u "$job_temp_dir/csv_fifo.XXXXXX") || { log "mktemp -u failed for csv_fifo: $?" "ERROR"; return 1; }
  mkfifo "$csv_fifo" || { log "mkfifo failed for $csv_fifo: $?" "ERROR"; rm -f "$csv_fifo"; return 1; }
  log "Created named pipe for data transfer: $csv_fifo" "DEBUG"

  local header_temp_file
  header_temp_file=$(mktemp "$job_temp_dir/header.XXXXXX") || { log "mktemp failed for header_temp_file: $?" "ERROR"; return 1; }

  set +e
  "$DECOMPRESS_TOOL" "${DECOMPRESS_FLAGS[@]}" "$absolute_file" | head -n1 > "$header_temp_file" 2>"$decomp_err_file"
  local exit_codes=("${PIPESTATUS[@]}")
  set -e

  local decomp_status=${exit_codes[0]:-UNKNOWN}
  local head_status=${exit_codes[1]:-UNKNOWN}

  local header_content
  header_content=$(cat "$header_temp_file" 2>/dev/null || echo "File '$header_temp_file' empty or unreadable")
  log "Header content read: [$header_content]" "DEBUG"
  local error_content
  error_content=$(cat "$decomp_err_file" 2>/dev/null || echo "File '$decomp_err_file' empty or unreadable")
  log "Decompression error content read: [$error_content]" "DEBUG"

  log "Header extraction pipe completed for $file. Decompress exit code: $decomp_status, Head exit code: $head_status" "DEBUG"

  local csv_header
  csv_header=$(cat "$header_temp_file")

  rm -f "$header_temp_file"

  if [[ $decomp_status -ne 0 && $decomp_status -ne 141 ]]; then
    log "Header decompression failed for $file (Exit code: $decomp_status)" "ERROR"
    write_tracking_file "$filename" "FAILED_TO_IMPORT"
    ((failed_imports++))
    log "Exiting import_file for $file due to header decompression failure" "ERROR"
    rm -f "$csv_fifo"
    return 1
  elif [[ $head_status -ne 0 ]]; then
    log "Head command failed for $file (Exit code: $head_status)" "ERROR"
    write_tracking_file "$filename" "FAILED_TO_IMPORT"
    ((failed_imports++))
    log "Exiting import_file for $file due to head command failure" "ERROR"
    rm -f "$csv_fifo"
    return 1
  elif [[ -z "$csv_header" ]]; then
    log "Could not read header from $file (Empty header string obtained)" "ERROR"
    write_tracking_file "$filename" "FAILED_TO_IMPORT"
    ((failed_imports++))
    log "Exiting import_file for $file due to empty header" "ERROR"
    rm -f "$csv_fifo"
    return 1
  fi

  log "Successfully extracted header for $file." "DEBUG"

  # Safely generate column list from CSV header using Python with CSV module
  local columns
  columns=$(python3 -c "
import sys
import csv
import io

try:
    # Convert header to properly quoted column list for SQL
    header_line = '''$csv_header'''
    csv_reader = csv.reader(io.StringIO(header_line))
    header_fields = next(csv_reader)

    quoted_columns = []
    for col in header_fields:
        # Double-quote for SQL
        quoted_columns.append('\"' + col + '\"')

    result = ','.join(quoted_columns)
    print(result)
except Exception as e:
    print(f'Error: {str(e)}', file=sys.stderr)
    sys.exit(1)
" 2>"$python_stderr_file")
  python_exit_code=$?

  if [[ $python_exit_code -ne 0 || -z "$columns" ]]; then
    log "Failed to generate column list from CSV header for $file" "ERROR"
    write_tracking_file "$filename" "FAILED_TO_IMPORT"
    ((failed_imports++))
    return 1
  fi

  log "Using $file column list from CSV header: $columns" "DEBUG"

  local db_columns
  db_columns=$(psql -t -c "SELECT STRING_AGG(column_name, ',' ORDER BY ordinal_position) FROM information_schema.columns WHERE table_name = '$table' AND table_schema = '${SCHEMA}'")
  db_query_exit_code=$?

  db_columns=$(echo "$db_columns" | tr -d ' ')

  local csv_column_count
  csv_column_count=$(echo "$csv_header" | tr ',' '\n' | wc -l)
  local db_column_count
  db_column_count=$(echo "$db_columns" | awk -F, '{print NF}')

  if [[ "$csv_column_count" != "$db_column_count" ]]; then
    log "Column count mismatch for $table (DB Query Exit: $db_query_exit_code): CSV has $csv_column_count columns, DB table has $db_column_count columns. Using CSV header for mapping." "WARN"
  else
    log "Column count matches between CSV and database for $table ($csv_column_count columns) (DB Query Exit: $db_query_exit_code)" "DEBUG"
  fi

  # Start decompression in background, writing to named pipe with on-the-fly hash calculation
  log "Starting decompression process for $file..." "DEBUG"
  "$DECOMPRESS_TOOL" "${DECOMPRESS_FLAGS[@]}" "$absolute_file" > "$csv_fifo" 2>"$decomp_err_file" &
  local decompress_pid=$!

  # Import using named pipe, with explicit column mapping for all tables
  log "Starting import process for $file..." "DEBUG"
  # Count rows using Python to handle quoted fields and embedded newlines in the CSV

  set +e
  cat "$csv_fifo" | tee >(python3 -c '
import sys
import csv
import traceback

try:
    # Set field size limit to 1GB to correctly read large data rows from some files
    csv.field_size_limit(1024 * 1024 * 1024)

    counter = 0
    reader = csv.reader(sys.stdin)
    for row in reader:
        counter += 1

    # Print final count to stdout for capturing in the row_counter file
    print(counter)

except Exception as e:
    print(f"Error in Python counter: {str(e)}", file=sys.stderr)
    print(traceback.format_exc(), file=sys.stderr)
    if counter > 0:
        print(counter)
    else:
        print(0)
' > "$row_counter") 2>/dev/null | \
      dd bs=16M iflag=fullblock status=none 2>/dev/null | \
      PGAPPNAME="$PGAPPNAME" \
      psql -v ON_ERROR_STOP=1 --single-transaction \
      -q -c "COPY $table ($columns) FROM STDIN WITH CSV HEADER DELIMITER ',';" \
      >"$stdout_file" 2>"$stderr_file"
  # Capture PIPESTATUS for the entire pipeline
  # Note: Indices depend on pipeline length: 0=cat, 1=tee, 2=dd, 3=psql
  pipeline_exit_codes=("${PIPESTATUS[@]}")
  set -e

  local cat_exit_code=${pipeline_exit_codes[0]:-UNKNOWN}
  local tee_or_python_exit_code=${pipeline_exit_codes[1]:-UNKNOWN}
  local dd_exit_code=${pipeline_exit_codes[2]:-UNKNOWN}
  local psql_exit_code=${pipeline_exit_codes[3]:-UNKNOWN}

  log "Pipeline exit codes for $file: cat=$cat_exit_code, tee/python=$tee_or_python_exit_code, dd=$dd_exit_code, psql=$psql_exit_code" "DEBUG"

  wait $decompress_pid
  decompress_exit_code=$?

  if jobs -p | grep -q .; then
    log "Waiting for background processes to complete..." "DEBUG"
    wait
  fi

  local total_lines
  total_lines=$(cat "$row_counter")
  local import_pipe_row_count=$((total_lines - 1))

  log "Import pipeline row count for $file: $import_pipe_row_count (total lines including header: $total_lines)" "DEBUG"

  log "Decompression exit code: $decompress_exit_code, PSQL exit code: $psql_exit_code for $file" "DEBUG"

  cat "$stdout_file" >> "$LOG_FILE"
  cat "$stderr_file" >> "$LOG_FILE"
  cat "$decomp_err_file" >> "$LOG_FILE"

  if [[ $psql_exit_code -ne 0 ]]; then
    log "Import failed for file: $file (Exit Code: $psql_exit_code). PSQL Error: [$(cat "$stderr_file" 2>/dev/null || echo "File '$stderr_file' empty or unreadable")]" "ERROR"

    # Try to diagnose common issues
    log "Diagnosing import issue for $file..."
    if grep -q "does not exist" "$stderr_file" 2>/dev/null; then
      log "Table or column does not exist. Check if the table schema matches the CSV header." "ERROR"
    elif grep -q "invalid input syntax" "$stderr_file" 2>/dev/null; then
      log "Invalid input syntax detected. Data may not match column types." "ERROR"
    elif grep -q "violates" "$stderr_file" 2>/dev/null; then
      log "Constraint violation detected. Data may violate table constraints." "ERROR"
    fi

    write_tracking_file "$filename" "FAILED_TO_IMPORT"
    ((failed_imports++))
    log "Exiting import_file for $file due to psql failure (exit code: $psql_exit_code)" "ERROR"
    return 1
  fi

  if [[ $tee_or_python_exit_code -ne 0 ]]; then
      log "Row counter (Python) failed with exit code $tee_or_python_exit_code, even though psql exited 0 for $file." "DEBUG"
      write_tracking_file "$filename" "FAILED_TO_IMPORT"
      ((failed_imports++))
      log "Exiting import_file for $file due to row counter failure." "ERROR"
      return 1
  fi

  if [[ $decompress_exit_code -ne 0 ]] && [ $decompress_exit_code -ne 141 ]; then
    log "Decompression failed for $file, exit code: $decompress_exit_code" "ERROR"
    write_tracking_file "$filename" "FAILED_TO_IMPORT"
    ((failed_imports++))
    log "Exiting import_file for $file due to decompression failure" "ERROR"
    return 1
  fi

  if [[ $cat_exit_code -ne 0 ]] || ! [[ "$total_lines" =~ ^[0-9]+$ ]]; then
      log "Error reading row counter or non-numeric value for $file. Raw value: '$total_lines' (cat exit code: $cat_exit_code)" "ERROR"
      log "All pipeline exit codes - cat: $cat_exit_code, tee/python: $tee_or_python_exit_code, dd: $dd_exit_code, psql: $psql_exit_code" "ERROR"
      write_tracking_file "$filename" "FAILED_TO_IMPORT"
      ((failed_imports++))
      log "Exiting import_file for $file due to row counter read/validation failure" "ERROR"
      return 1
  fi

  log "Successfully imported $file"

  if [[ -z "$expected_count" || "$expected_count" == "N/A" ]]; then
    log "No expected row count for $file in manifest, skipping verification."
    write_tracking_file "$filename" "IMPORTED"
    return 0
  fi

  if [[ "$import_pipe_row_count" == "$expected_count" ]]; then
    log "Row count verified for $file using import pipeline counter: $import_pipe_row_count (expected: $expected_count)"
    write_tracking_file "$filename" "IMPORTED" "HASH_VERIFIED"
    return 0
  else
    log "Import pipeline row count ($import_pipe_row_count) doesn't match expected count ($expected_count) for $file. Proceeding to boundary check..." "WARN"
  fi

  log "Primary row count failed for ${file}, attempting generic existence check..." "WARN"

  local existence_check
  log "Starting existence check query for $file..." "DEBUG"
  existence_check=$(psql -v ON_ERROR_STOP=1 -q -Atc "SELECT EXISTS(SELECT 1 FROM ${table} LIMIT 1);")
  local existence_status=$?
  log "Finished existence check query for $file." "DEBUG"

  if [ $existence_status -eq 0 ]; then
    if [ "$existence_check" == "t" ]; then
      log "Existence verification successful for ${file}, table ${table} contains data (existence check)" "WARN"
      write_tracking_file "$filename" "IMPORTED" "ROW_COUNT_UNVERIFIED"
      return 0
    else
      log "Existence verification failed: table ${table} appears to be empty" "ERROR"
    fi
  else
    log "Existence verification failed: could not query table ${table}" "ERROR"
  fi

  log "All verification methods failed for $file" "ERROR"
  write_tracking_file "$filename" "FAILED_TO_IMPORT"
  ((failed_imports++))
  return 1
}

initialize_database() {
  enable_pipefail
  trap 'disable_pipefail' RETURN

  # Reconstruct manifest_tables from serialized data
  declare -A manifest_tables
  eval "$MANIFEST_TABLES_SERIALIZED"

  if [[ ! -f "$IMPORT_DIR/schema.sql" ]]; then
    log "schema.sql not found in $IMPORT_DIR." "ERROR"
    exit 1
  fi

  INIT_SH_URL="https://raw.githubusercontent.com/hiero-ledger/hiero-mirror-node/refs/heads/main/importer/src/main/resources/db/scripts/init.sh"

  log "Downloading init.sh from $INIT_SH_URL"

  if curl -fSLs -o "init.sh" "$INIT_SH_URL"; then
    log "Successfully downloaded init.sh"
  else
    log "Failed to download init.sh" "ERROR"
    exit 1
  fi

  chmod +x init.sh

  log "Initializing the database using init.sh"
  if ./init.sh >> "$LOG_FILE" 2>&1; then
    log "Database initialized successfully"
  else
    log "Database initialization failed. Check $LOG_FILE for details." "ERROR"
    exit 1
  fi

  # Update PostgreSQL environment variables to connect to 'mirror_node' database as 'mirror_node' user
  export PGUSER="mirror_node"
  export PGDATABASE="mirror_node"
  export PGPASSWORD="$OWNER_PASSWORD"

  log "Executing schema.sql from $IMPORT_DIR"
  if psql -v ON_ERROR_STOP=1 -q -f "$IMPORT_DIR/schema.sql" >> "$LOG_FILE" 2>&1; then
    log "schema.sql executed successfully"
  else
    log "Failed to execute schema.sql. Check $LOG_FILE for details." "ERROR"
    exit 1
  fi

  # Check that each table exists in the database
  if ! psql -v ON_ERROR_STOP=1 -c '\q' >/dev/null 2>&1; then
    log "Unable to connect to the PostgreSQL database." "ERROR"
    exit 1
  fi
  log "Successfully connected to the PostgreSQL database."

  missing_tables=()
  declare -A checked_tables_map=()
  log "Checking table existence in the database"

  for table in "${!manifest_tables[@]}"; do
    log "Verifying existence of table: $table"

    if [[ -n "${checked_tables_map["$table"]:-}" ]]; then
      log "Table $table has already been checked. Skipping."
      continue
    fi
    checked_tables_map["$table"]=1

    if ! psql -v ON_ERROR_STOP=1 -qt -c "SELECT 1 FROM pg_class WHERE relname = '$table' AND relnamespace = '${SCHEMA}'::regnamespace;" | grep -q 1; then
      missing_tables+=("$table")
      log "$table missing from database" "ERROR"
    else
      log "$table exists in the database"
    fi
  done

  if [[ ${#missing_tables[@]} -gt 0 ]]; then
    log "====================================================" "ERROR"
    log "The following tables are missing in the database:" "ERROR"
    for table in "${missing_tables[@]}"; do
      log "- $table" "ERROR"
    done
    log "====================================================" "ERROR"
    exit 1
  else
    log "All tables exist in the database."
  fi

  return 0
}

# Background function to monitor import progress
monitor_progress() {
  local p_user="$1"
  local p_database="$2"
  local p_host="$3"
  local p_port="$4"
  local p_password="$5"

  export PGUSER="$p_user"
  export PGDATABASE="$p_database"
  export PGHOST="$p_host"
  export PGPORT="$p_port"
  export PGPASSWORD="$p_password"

  # Set locale for proper number formatting with thousands separators
  export LC_NUMERIC="en_US.UTF-8"

  mkdir -p "$MONITOR_TEMP_DIR"
  trap 'log "Monitor self-cleanup trap triggered for $MONITOR_TEMP_DIR" "DEBUG"; rm -rf "$MONITOR_TEMP_DIR"' EXIT

  sleep 5
  log "Starting progress monitor (Interval: ${PROGRESS_INTERVAL}s, Output: ${PROGRESS_FILE})"

  touch "$MONITOR_STATE_FILE"

  if [[ ! -s "$MONITOR_STATE_FILE" ]]; then
    echo -e "initialization\t0\t$(date +%s)" > "$MONITOR_STATE_FILE"
  fi

  rm -f "$MONITOR_STOP_SIGNAL_FILE"

  while true; do
    # Check for stop signal
    if [[ -f "$MONITOR_STOP_SIGNAL_FILE" ]]; then
      log "Monitor stop signal received. Exiting monitor loop."
      break
    fi

    local current_timestamp
    current_timestamp=$(date +%s)

    declare -A prev_counts
    declare -A prev_times
    if [[ -f "$MONITOR_STATE_FILE" && -s "$MONITOR_STATE_FILE" ]]; then
      while IFS=$'\t' read -r filename prev_count prev_time; do
        prev_counts["$filename"]=$prev_count
        prev_times["$filename"]=$prev_time
      done < "$MONITOR_STATE_FILE"
    fi

    local table_check
    table_check=$(psql -X -q -t -c "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_schema = '${SCHEMA}' and table_name = '${PROGRESS_DB_TABLE}');" | tr -d ' ')

    if [[ "$table_check" != "t" ]]; then
      log "Progress table ${PROGRESS_DB_TABLE} does not exist, creating an empty version" "WARN"

      # Create a basic version of the table so the query doesn't fail
      if ! psql -v ON_ERROR_STOP=1 -q -c """
      CREATE UNLOGGED TABLE IF NOT EXISTS ${PROGRESS_DB_TABLE} (
        filename TEXT PRIMARY KEY,
        expected_count BIGINT
      );
      """; then
        log "Failed to create ${PROGRESS_DB_TABLE} table" "ERROR"
        echo "Error: Failed to create progress monitoring table. See bootstrap.log for details." > "$PROGRESS_FILE"
        sleep "$PROGRESS_INTERVAL"
        continue
      fi
    fi

    local stderr_file
    stderr_file=$(mktemp "$MONITOR_TEMP_DIR/stderr.XXXXXX") || { log "mktemp failed for stderr_file in monitor_progress: $?" "ERROR"; return 1; }
    local temp_data
    temp_data=$(mktemp "$MONITOR_TEMP_DIR/temp_data.XXXXXX") || { log "mktemp failed for temp_data in monitor_progress: $?" "ERROR"; return 1; }

    psql -X -q -v ON_ERROR_STOP=1 <<EOF > "$temp_data" 2>"$stderr_file"
\\pset format unaligned
\\pset fieldsep '|'
\\pset tuples_only on
\\pset footer off

WITH app_names AS (
  SELECT
    a.pid,
    application_name,
    substring(application_name from '^bootstrap_copy_(.*)$') AS filename,
    COALESCE(c.tuples_processed, 0) as tuples_processed
  FROM pg_stat_activity a
  LEFT JOIN pg_stat_progress_copy c ON a.pid = c.pid
  WHERE a.state = 'active'
  AND a.application_name LIKE 'bootstrap_copy_%'
)
SELECT
  app.filename,
  app.tuples_processed,
  COALESCE(m.expected_count, 0) as expected_count
FROM app_names app
LEFT JOIN ${PROGRESS_DB_TABLE} m ON
  app.filename = SPLIT_PART(m.filename, '/', array_length(string_to_array(m.filename, '/'), 1))
ORDER BY app.filename;
EOF

    local psql_status=$?

    if [[ $psql_status -ne 0 ]]; then
      log "Progress monitor query failed with status: $psql_status" "ERROR"
      log "PostgreSQL error output: [$(cat "$stderr_file" 2>/dev/null || echo 'Error reading stderr file or file empty')" "ERROR"
      if [[ -s "$temp_data" ]]; then
        log "Partial query output: $(cat "$temp_data")" "DEBUG"
      fi
      echo "ERROR: Progress monitoring query failed, progress monitor will not be displayed" > "$PROGRESS_FILE"
      rm -f "$temp_data" "$stderr_file"
      sleep "$PROGRESS_INTERVAL"
      continue
    fi

    {
      printf "%-32s %-18s %-16s %-15s %-15s\n" "Filename" "Rows_Processed" "Total_Rows" "Percentage" "Rate(rows/s)"
      printf "%-32s %-18s %-16s %-15s %-15s\n" "$(printf -- '-%.0s' {1..32})" "$(printf -- '-%.0s' {1..18})" "$(printf -- '-%.0s' {1..16})" "$(printf -- '-%.0s' {1..15})" "$(printf -- '-%.0s' {1..15})"

      local new_state
      new_state=$(mktemp "$MONITOR_TEMP_DIR/new_state.XXXXXX") || { log "mktemp failed for new_state in monitor_progress: $?" "ERROR"; return 1; }

      while IFS='|' read -r filename tuples_processed expected_count; do
        if [[ -z "$filename" ]]; then
          continue
        fi

        # Format numbers with commas
        local formatted_processed
        formatted_processed=$(printf "%'d" "$tuples_processed")
        local formatted_expected
        formatted_expected=$(printf "%'d" "$expected_count")

        # Calculate percentage
        local percentage="0%"
        if [[ $expected_count -gt 0 ]]; then
          percentage=$(printf "%.2f%%" "$(echo "scale=4; $tuples_processed * 100 / $expected_count" | bc)")
        fi

        # Calculate rate per second
        local rate="calculating..."
        basename_filename=$(basename "$filename" 2>/dev/null || echo "$filename")

        if [[ -n "${prev_counts[$basename_filename]}" && -n "${prev_times[$basename_filename]}" ]]; then
          local prev_count="${prev_counts[$basename_filename]}"
          local prev_time="${prev_times[$basename_filename]}"

          if [[ $current_timestamp -gt $prev_time &&
                $(( tuples_processed - prev_count )) -ge 0 &&
                $(( current_timestamp - prev_time )) -gt 0 ]]; then
            local diff_rows=$(( tuples_processed - prev_count ))
            local diff_time=$(( current_timestamp - prev_time ))
            rate=$(printf "%'d" "$(( diff_rows / diff_time ))")
          fi
        fi

        echo -e "$basename_filename\t$tuples_processed\t$current_timestamp" >> "$new_state"

        # All columns left-aligned with increased spacing
        printf "%-32s %-18s %-16s %-15s %-15s\n" \
          "$filename" "$formatted_processed" "$formatted_expected" "$percentage" "$rate"
      done < "$temp_data"

    } > "$PROGRESS_FILE"

    if [[ -s "$new_state" ]]; then
      mv "$new_state" "$MONITOR_STATE_FILE"
    else
      rm -f "$new_state"
    fi

    rm -f "$temp_data"
    rm -f "$stderr_file"

    sleep "$PROGRESS_INTERVAL"
  done

  rm -f "$MONITOR_STOP_SIGNAL_FILE"

  if [[ -f "$PROGRESS_FILE" ]]; then
    log "Monitor cleaning up progress file: $PROGRESS_FILE" "DEBUG"
    rm -f "$PROGRESS_FILE"
  fi
}

print_final_statistics() {
  local special_file_count=2  # schema.sql.gz and MIRRORNODE_VERSION.gz
  total_jobs_adjusted=$((total_jobs + special_file_count))

  log "===================================================="
  log "Import statistics:"
  log "Total files processed: $((total_jobs_adjusted + skipped_jobs))"
  log "Files skipped (already imported): $skipped_jobs"
  log "Regular files attempted to import: $total_jobs"
  log "Special files processed: $special_file_count"
  log "Total attempted imports: $total_jobs_adjusted"
  log "Files completed: $completed_jobs"
  log "Files failed: $failed_imports"
  log "Files with inconsistent status: $inconsistent_files"
  log "Total files with issues: $((failed_imports + inconsistent_files))"
  log "===================================================="

  # Summarize discrepancies
  if [[ -s "$DISCREPANCY_FILE" ]]; then
    overall_success=false
    log "===================================================="
    log "Discrepancies detected during import:"
    log "The following files failed the row count verification:"
    log ""
    while read -r line; do
      log "- $line"
    done < "$DISCREPANCY_FILE"
    log "===================================================="
  else
    log "No discrepancies detected during import."
  fi

  # Log the final status of the import process
  if [[ $overall_success = true ]]; then
    log "===================================================="
    log "DB import completed successfully."
    log "The database is fully identical to the data files."
    log "===================================================="
  else
    log "===================================================="
    log "The database import process encountered errors and is incomplete." "ERROR"
    log "Mirrornode requires a fully synchronized database." "ERROR"
    log "Please review the discrepancies above." "ERROR"
    log "===================================================="
  fi
}

####################################
# Execution
####################################

trap 'cleanup INT' SIGINT
trap 'cleanup TERM' SIGTERM
trap 'cleanup PIPE' SIGPIPE
trap 'cleanup EXIT' EXIT

check_bash_version

PGID=$(ps -o pgid= $$ | tr -d ' ')
log "Script Process Group ID: $PGID"

if [[ $# -eq 0 ]]; then
  log "No arguments provided. Use --help or -h for usage information." "ERROR"
  show_help
  exit 1
fi

while [[ "$#" -gt 0 ]]; do
  case $1 in
  -h | --help | -H)
    show_help
    exit 0
    ;;
  *)
    break
    ;;
  esac
  shift
done

DB_CPU_CORES="$1"
shift

while [[ $# -gt 0 ]]; do
  case $1 in
    --full)
      USE_FULL_DB=1
      shift
      ;;
    *)
      IMPORT_DIR="$1"
      shift
      ;;
  esac
done

if [[ -z "$DB_CPU_CORES" || -z "$IMPORT_DIR" ]]; then
    log "Missing required arguments. Use --help or -h for usage information" "ERROR"
    exit 1
fi

if ! [[ "$DB_CPU_CORES" =~ ^[1-9][0-9]*$ ]]; then
    log "DB_CPU_CORES must be a positive integer" "ERROR"
    exit 1
fi

IMPORT_DIR="$(realpath "$IMPORT_DIR")"

if [[ ! -d "$IMPORT_DIR" ]]; then
  log "IMPORT_DIR '$IMPORT_DIR' does not exist or is not a directory" "ERROR"
  exit 1
fi

AVAILABLE_CORES=$(nproc)
DB_AVAILABLE_CORES=$((DB_CPU_CORES))
if (( DB_AVAILABLE_CORES <= 0 )); then
  DB_AVAILABLE_CORES=1  # Minimum 1 core
fi
log "DB_AVAILABLE_CORES set to: $DB_AVAILABLE_CORES"

source_bootstrap_env

if ! check_required_tools; then
    exit 1
fi

if [[ -n "$USE_FULL_DB" ]]; then
    MANIFEST_FILE="${IMPORT_DIR}/manifest.csv"
else
    MANIFEST_FILE="${IMPORT_DIR}/manifest.minimal.csv"
fi
MIRRORNODE_VERSION_FILE="$IMPORT_DIR/MIRRORNODE_VERSION"
log "Using manifest file: $MANIFEST_FILE"

if [[ ! -f "$MANIFEST_FILE" ]]; then
  log "Manifest file not found: $MANIFEST_FILE" "ERROR"
  exit 1
fi

if [[ $AVAILABLE_CORES -lt $DB_AVAILABLE_CORES ]]; then
  MAX_JOBS="$AVAILABLE_CORES"
else
  MAX_JOBS="$DB_AVAILABLE_CORES"
fi
MAX_JOBS=$((MAX_JOBS - 1))

process_manifest

if ! validate_special_files; then
  exit 1
fi

for file in "$IMPORT_DIR/schema.sql.gz" "$IMPORT_DIR/MIRRORNODE_VERSION.gz"; do
  decompress_flags_keep=("${DECOMPRESS_FLAGS[@]}")
  for i in "${!decompress_flags_keep[@]}"; do
    if [[ "${decompress_flags_keep[$i]}" == "-c" ]]; then
      decompress_flags_keep[i]="-k"
    fi
  done

  if ! "$DECOMPRESS_TOOL" "${decompress_flags_keep[@]}" -f "$file" 2>/dev/null; then
    log "Error decompressing $file using $DECOMPRESS_TOOL" "ERROR"
    exit 1
  fi
done

# Serialize manifest data for subshell access
MANIFEST_COUNTS_SERIALIZED=$(declare -p manifest_counts)
MANIFEST_TABLES_SERIALIZED=$(declare -p manifest_tables)

if [[ -f "$MIRRORNODE_VERSION_FILE" ]]; then
  MIRRORNODE_VERSION=$(tr -d '[:space:]' < "$MIRRORNODE_VERSION_FILE")
  log "Compatible Mirrornode version: $MIRRORNODE_VERSION"
else
  log "MIRRORNODE_VERSION file not found in $IMPORT_DIR." "ERROR"
  exit 1
fi

log "Starting DB import."

if [[ ! -f "$DB_SKIP_FLAG_FILE" ]]; then
  initialize_database
  touch "$DB_SKIP_FLAG_FILE" # Create a flag to skip subsequent runs from running db init after it succeeded once
else
  export PGUSER="mirror_node"
  export PGDATABASE="mirror_node"
  export PGPASSWORD="$OWNER_PASSWORD"
  log "Set PGUSER, PGDATABASE, and PGPASSWORD for PostgreSQL."

  if ! verify_postgres_connection; then
    exit 1
  fi
  log "Database is already initialized, skipping initialization."
fi

if ! load_manifest_to_db; then
  log "Failed to load manifest counts to DB on subsequent run. Cannot start progress monitor." "ERROR"
else
  log "Starting background progress monitor..."
  monitor_progress "$PGUSER" "$PGDATABASE" "$PGHOST" "$PGPORT" "$PGPASSWORD" &
  monitor_pid=$!
  pids+=("$monitor_pid")
  log "Progress monitor started with PID: $monitor_pid"
fi

# Get the list of files to import
mapfile -t files < <(collect_import_tasks)

# Initialize the tracking file with all files as NOT_STARTED and HASH_UNVERIFIED
init_cmd="for file in \"\${files[@]}\"; do
  # Only add if not already in tracking file
  if [[ -z \"\$(read_tracking_status \"\$(basename \"\$file\")\")\" ]]; then
    echo \"\$(basename \"\$file\") NOT_STARTED HASH_UNVERIFIED\" >> \"$TRACKING_FILE\"
  fi
done"

with_lock "$TRACKING_LOCK_FILE" "$init_cmd"

# Initialize variables for background processes
overall_success=true
# Initialize as a shared variable accessible to all subprocesses
export failed_imports=0
declare -a import_pids=() # Array specifically for import job PIDs

# Export required functions and variables for subshell usage
export -f \
  log show_help check_bash_version check_required_tools \
  determine_decompression_tool kill_descendants cleanup write_tracking_file read_tracking_status \
  collect_import_tasks write_discrepancy source_bootstrap_env process_manifest validate_file \
  validate_special_files initialize_database import_file get_table_name_from_filename retry_query \
  find_full_path verify_postgres_connection with_lock print_final_statistics

export \
  DECOMPRESS_TOOL DECOMPRESS_FLAGS BOOTSTRAP_ENV_FILE DISCREPANCY_FILE \
  IMPORT_DIR LOG_FILE MANIFEST_FILE TRACKING_FILE LOCK_FILE MAX_JOBS \
  PID_FILE LOG_FILE_LOCK DISCREPANCY_FILE_LOCK PROGRESS_FILE PROGRESS_INTERVAL \
  PROGRESS_DB_TABLE MONITOR_STATE_FILE MONITOR_STOP_SIGNAL_FILE \
  SCRIPT_TEMP_DIR MONITOR_TEMP_DIR DEBUG_MODE

declare -A pid_to_file

total_jobs=0
completed_jobs=0
skipped_jobs=0

for file in "${files[@]}"; do
  base_file=$(basename "$file")
  current_status=$(read_tracking_status "$file")
  log "Checking file: $base_file - Status read from tracking: [$current_status]" "DEBUG"
  if [[ "$current_status" == "IMPORTED" ]]; then
    log "Skipping already imported file: $base_file"
    ((skipped_jobs++))
    continue
  else
    log "Processing file with non-IMPORTED status: $base_file (Current status: $current_status)"
    write_tracking_file "$base_file" "IN_PROGRESS"
  fi

  while [[ $(jobs -rp | wc -l) -ge $MAX_JOBS ]]; do
    sleep 1
  done

  # Start import with PID tracking
  import_file "$file" &
  pid=$!
  pid_to_file["$pid"]="$file"
  pids+=("$pid")
  import_pids+=("$pid") # Add to import-specific PID array
  total_jobs=$((total_jobs + 1))
done

log "All import jobs started. Total jobs: $total_jobs, PIDs count: ${#pids[@]}"

disable_pipefail

# Wait for all import jobs to finish (excluding monitor)
log "Waiting for all import jobs to complete..."
for pid in "${import_pids[@]}"; do
  target_file="${pid_to_file[$pid]}"
  base_file=$(basename "$target_file")

  current_hash_status=$(grep "^$base_file " "$TRACKING_FILE" 2>/dev/null | awk '{print $3}')
  if [[ -z "$current_hash_status" ]]; then
      log "Could not read current hash status for $base_file from tracking file, defaulting to HASH_UNVERIFIED." "WARN"
      current_hash_status="HASH_UNVERIFIED"
  fi

  # Check if the process still exists before waiting
  if kill -0 "$pid" 2>/dev/null; then
    if ! wait "$pid"; then
      exit_status=$?
      overall_success=false
      log "Import job failed for file: '$base_file' (PID: $pid, Exit Status: $exit_status)" "ERROR"

      job_temp_dir="$SCRIPT_TEMP_DIR/import-jobs/$pid"
      actual_stderr_file=$(find "$job_temp_dir" -name "stderr.*" -print -quit 2>/dev/null)
      if [[ -n "$actual_stderr_file" && -s "$actual_stderr_file" ]]; then
        log "--- Stderr for $base_file (PID: $pid) ---" "ERROR"
        log "$(sed 's/^/    /' "$actual_stderr_file")" "ERROR"
        log "--- End Stderr for $base_file (PID: $pid) ---" "ERROR"
      else
        log "Stderr file for failed job (PID: $pid) not found or empty in $job_temp_dir." "WARN"
      fi

      write_tracking_file "$base_file" "FAILED_TO_IMPORT"
      ((failed_imports++))
    else
      log "Import job completed successfully for file: '$base_file' (PID: $pid)"
      write_tracking_file "$base_file" "IMPORTED"
      ((completed_jobs++))
    fi
  else
    current_status=$(read_tracking_status "$base_file")
    # If it was marked IN_PROGRESS, verify if import actually succeeded by checking for error files or database content
    if [[ "$current_status" == "IN_PROGRESS" ]]; then
      log "Status for already finished process $pid (File: $base_file) is IN_PROGRESS. Verifying actual outcome." "WARN"
      job_temp_dir="$SCRIPT_TEMP_DIR/import-jobs/$pid"
      actual_stderr_file=$(find "$job_temp_dir" -name "stderr.*" -print -quit 2>/dev/null)
      if [[ -n "$actual_stderr_file" && -s "$actual_stderr_file" ]]; then
        log "Error detected in stderr for $base_file (PID: $pid). Marking as failed." "ERROR"
        log "--- Stderr for $base_file (PID: $pid) ---" "ERROR"
        log "$(sed 's/^/    /' "$actual_stderr_file")" "ERROR"
        log "--- End Stderr for $base_file (PID: $pid) ---" "ERROR"
        write_tracking_file "$base_file" "FAILED_TO_IMPORT"
        ((failed_imports++))
      else
        log "No error file found for $base_file (PID: $pid). Checking database state to confirm success." "WARN"
        table_name=$(get_table_name_from_filename "$base_file")
        if [[ -z "$table_name" ]]; then
          log "Could not determine table name for $base_file (PID: $pid). Cannot verify database state." "ERROR"
          write_tracking_file "$base_file" "FAILED_TO_IMPORT"
          ((failed_imports++))
        else
          row_check=$(psql -v ON_ERROR_STOP=1 -q -Atc "SELECT EXISTS(SELECT 1 FROM $table_name LIMIT 1);" 2>/dev/null)
          row_check_status=$?
          if [[ $row_check_status -eq 0 && "$row_check" == "t" ]]; then
            log "Database table $table_name contains data for $base_file (PID: $pid). Marking as successful." "INFO"
            write_tracking_file "$base_file" "IMPORTED"
            ((completed_jobs++))
          elif [[ $row_check_status -ne 0 ]]; then
            log "Database query failed for $base_file (PID: $pid) with status $row_check_status. Assuming success to avoid stuck status." "WARN"
            write_tracking_file "$base_file" "IMPORTED"
            ((completed_jobs++))
          else
            log "Database table $table_name is empty for $base_file (PID: $pid). Marking as failed." "ERROR"
            write_tracking_file "$base_file" "FAILED_TO_IMPORT"
            ((failed_imports++))
          fi
        fi
      fi
    elif [[ "$current_status" == "IMPORTED" ]]; then
      # Avoid double counting completed jobs if reconciling later
      ((completed_jobs++))
    elif [[ "$current_status" == "FAILED_TO_IMPORT" || "$current_status" == "FAILED_VALIDATION" ]]; then
      log "Status for already finished process $pid (File: $base_file) was already FAILED ($current_status)." "WARN"
      # Avoid double counting failed jobs
      ((failed_imports++))
    else
      log "Status for already finished process $pid (File: $base_file) is '$current_status'. Not updating status, but counting as completed for now." "WARN"
      # Assume completed for statistical purposes, reconcile later if needed.
      ((completed_jobs++))
    fi
  fi
done
log "Finished waiting for all import jobs to complete. Jobs: total=$total_jobs, completed=$completed_jobs, failed=$failed_imports"

# Signal the monitor process to stop
log "Signaling monitor process (PID: $monitor_pid) to stop..."
touch "$MONITOR_STOP_SIGNAL_FILE"

# Wait for the monitor process to exit gracefully
if [[ -n "$monitor_pid" && "$monitor_pid" -gt 0 ]]; then
  log "Waiting for monitor process (PID: $monitor_pid) to exit..."
  if wait "$monitor_pid"; then
    log "Monitor process (PID: $monitor_pid) exited successfully."
  else
    log "Monitor process (PID: $monitor_pid) exited with status $?." "WARN"
  fi
else
  log "Monitor PID not found or invalid, cannot wait for it." "WARN"
fi

enable_pipefail

# Make sure there are no remaining background processes before printing statistics
wait

disable_pipefail

log "Performing state consistency check..."
incomplete_count=$(grep -v -E "IMPORTED|FAILED_TO_IMPORT|FAILED_VALIDATION" "$TRACKING_FILE" | wc -l)
inconsistent_files=0
if [[ $incomplete_count -gt 0 ]]; then
  overall_success=false
  log "Found $incomplete_count files with incomplete status:" "ERROR"
  grep -v -E "IMPORTED|FAILED_TO_IMPORT|FAILED_VALIDATION" "$TRACKING_FILE"
  inconsistent_files=$incomplete_count
fi

final_completed=$(grep -c "IMPORTED" "$TRACKING_FILE" || true)
final_failed=$(grep -c -E "FAILED_VALIDATION|FAILED_TO_IMPORT" "$TRACKING_FILE" || true)

if [[ "$final_completed" -ne "$completed_jobs" ]]; then
    log "Reconciling completed jobs count: initial=$completed_jobs, final_tracking=$final_completed" "DEBUG"
    completed_jobs=$final_completed
fi
if [[ "$final_failed" -ne "$failed_imports" ]]; then
    log "Reconciling failed jobs count: initial=$failed_imports, final_tracking=$final_failed" "DEBUG"
    failed_imports=$final_failed
    overall_success=false
fi

# Ensure total processed = completed + failed + inconsistent
processed_check=$((completed_jobs + failed_imports + inconsistent_files))
special_file_count=2  # schema.sql.gz and MIRRORNODE_VERSION.gz
total_jobs_adjusted=$((total_jobs + special_file_count))
if [[ $processed_check -ne $total_jobs_adjusted ]]; then
    log "Count mismatch warning: Completed($completed_jobs) + Failed($failed_imports) + Inconsistent($inconsistent_files) = $processed_check != Total($total_jobs_adjusted)" "WARN"
fi

if [[ "$failed_imports" -gt 0 || "$inconsistent_files" -gt 0 ]]; then
  overall_success=false
fi

enable_pipefail
print_final_statistics

# Force cleanup of any remaining background processes for a clean exit
if [ "$(jobs -rp | wc -l)" -gt 0 ]; then
  log "Cleaning up remaining background jobs before exit..."
  for job_pid in $(jobs -rp); do
    kill -9 "$job_pid" 2>/dev/null || true
  done
  wait 2>/dev/null || true
fi

log "Script execution complete."
exit $((1 - overall_success))
