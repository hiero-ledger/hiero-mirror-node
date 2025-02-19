#!/usr/bin/env bash

# Start a new process group and detach from terminal
set -m
exec 1>/dev/null 2>>bootstrap.log
[[ -t 1 ]] && exec </dev/null >&0 2>&0

# Global variables
CLEANUP_IN_PROGRESS_FILE="bootstrap.cleanup"
export CLEANUP_IN_PROGRESS_FILE

# Save original stderr and redirect it to suppress job control messages
exec 3>&2
exec 2>/dev/null

# Write script's PID to $PID_FILE for process management
PID_FILE="bootstrap.pid"
echo $$ > "$PID_FILE"

####################################
# Variables
####################################

# Minimum required Bash version
REQUIRED_BASH_MAJOR=4
REQUIRED_BASH_MINOR=3

# Status tracking and logging files
LOG_FILE="bootstrap.log"                    # Main log file
TRACKING_FILE="bootstrap_tracking.txt"      # Import status tracking
LOCK_FILE="bootstrap_tracking.lock"         # File locking for thread safety
DISCREPANCY_FILE="bootstrap_discrepancies.log"  # Verification failures

# Required system tools
# Note: A decompressor (rapidgzip, igzip, or gunzip) is checked separately
REQUIRED_TOOLS=("psql" "realpath" "flock" "curl" "b3sum")

# Initialize tracking variables
export DECOMPRESS_TOOL=""          # Decompression tool to use (rapidgzip, igzip, or gunzip)
export DECOMPRESS_FLAGS=""         # Flags for the decompression tool command
export DECOMPRESSOR_CHECKED=false  # Track if decompressor check has been performed
MISSING_TOOLS=()                   # List of missing required tools

# Skip database initialization if this file exists
export DB_SKIP_FLAG_FILE="SKIP_DB_INIT"    # Flag file to skip database initialization

# Manifest selection (manifest.csv or manifest.minimal.csv)
USE_FULL_DB=""              # Use full manifest flag
MANIFEST_FILE=""            # Path to the manifest file

# Parallel processing configuration
B3SUM_NUM_THREADS=1         # Number of threads for BLAKE3 hash calculation
MAX_JOBS=""                 # Maximum number of concurrent import jobs

# Associative array for manifest row counts
declare -A manifest_counts  # Map of filename to expected row count

# Process tracking arrays
declare -a pids=()          # List of background process IDs
declare -A processing_files=()  # Map of files currently being processed

####################################
# Functions
####################################

# Enable/disable pipefail for error handling
enable_pipefail() {
  set -euo pipefail
}

disable_pipefail() {
  set +euo pipefail
}

export -f enable_pipefail disable_pipefail

# Log messages with UTC timestamps to $LOG_FILE
log() {
  local msg="$1"
  local level="${2:-INFO}"

  # During cleanup, only log messages from cleanup itself
  if [[ -f "$CLEANUP_IN_PROGRESS_FILE" && "$level" != "TERMINATE" ]]; then
    return
  fi

  local timestamp
  timestamp=$(date -u '+%Y-%m-%d %H:%M:%S')

  echo "[$timestamp] [$level] $msg" >> "$LOG_FILE"
}

# Display usage instructions and command-line options directly to the terminal
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

# Verify minimum required Bash version (4.3+)
check_bash_version() {
  local current_major=${BASH_VERSINFO[0]}
  local current_minor=${BASH_VERSINFO[1]}

  if (( current_major < REQUIRED_BASH_MAJOR )) || \
    (( current_major == REQUIRED_BASH_MAJOR && current_minor < REQUIRED_BASH_MINOR )); then
    log "Bash version ${REQUIRED_BASH_MAJOR}.${REQUIRED_BASH_MINOR}+ is required. Current version is ${BASH_VERSION}." "ERROR"
    exit 1
  fi
}

# Verify presence of required system tools and optimal decompressors
check_required_tools() {
  # Check required tools
  for tool in "${REQUIRED_TOOLS[@]}"; do
    if ! command -v "$tool" &> /dev/null; then
        MISSING_TOOLS+=("$tool")
    fi
  done

  # Always check for a decompressor (at minimum gunzip must exist)
  if ! $DECOMPRESSOR_CHECKED; then
    if ! determine_decompression_tool; then
      log "No decompression tool found - at minimum gunzip is required" "ERROR"
      log "Recommended tools for faster decompression:" "ERROR"
      log "  - rapidgzip (fastest): https://github.com/mxmlnkn/rapidgzip" "ERROR"
      log "  - igzip (next best): https://github.com/intel/isa-l" "ERROR"
    fi
    DECOMPRESSOR_CHECKED=true
  fi

  # Report all missing tools at once if any
  if [ "${#MISSING_TOOLS[@]}" -gt 0 ]; then
    log "The following required tools are not installed:" "ERROR"
    # Use sort -u to remove duplicates
    printf "%s\n" "${MISSING_TOOLS[@]}" | sort -u | while read -r tool; do
      log "  - $tool" "ERROR"
    done
    log "Please install them to continue." "ERROR"
    return 1
  fi

  return 0
}

# Select fastest available decompression tool (rapidgzip > igzip > gunzip)
determine_decompression_tool() {
  if command -v rapidgzip >/dev/null 2>&1; then
    DECOMPRESS_TOOL="rapidgzip"
    DECOMPRESS_FLAGS="-d -c -P1"
    log "Using rapidgzip for decompression"
    return 0
  fi

  if command -v igzip >/dev/null 2>&1; then
    DECOMPRESS_TOOL="igzip"
    DECOMPRESS_FLAGS="-d -c -T1"
    log "Using igzip for decompression"
    return 0
  fi

  if command -v gunzip >/dev/null 2>&1; then
    DECOMPRESS_TOOL="gunzip"
    DECOMPRESS_FLAGS="-c"
    log "Using gunzip for decompression"
    return 0
  fi

  # No decompression tools found
  MISSING_TOOLS+=("gunzip")
  return 1
}

# Recursively terminate a process and all its child processes
kill_descendants() {
  local pid="$1"
  local children
  children=$(pgrep -P "$pid" 2>/dev/null)
  for child in $children; do
    kill_descendants "$child"
  done
  kill -9 "$pid" >/dev/null 2>&1
}

# Clean up resources and terminate child processes on script exit
cleanup() {
  disable_pipefail
  local trap_type="$1"

  if [[ "$trap_type" == "INT" || "$trap_type" == "TERM" ]]; then
    # Redirect stderr to /dev/null for the remainder of cleanup
    exec 2>/dev/null
    touch "$CLEANUP_IN_PROGRESS_FILE"

    # If a tracking file update was in progress, complete it
    if [[ -f "${TRACKING_FILE}.tmp" ]]; then
      mv "${TRACKING_FILE}.tmp" "$TRACKING_FILE"
    fi

    # First kill all psql processes to stop any active queries
    pkill -9 psql

    # Kill all decompression tool processes
    pkill -9 "$DECOMPRESS_TOOL"

    # Kill all background jobs and their descendants
    for pid in "${pids[@]}"; do
      if kill -0 "$pid" 2>/dev/null; then
        kill_descendants "$pid"
      fi
    done

    # Force kill any remaining children immediately
    pkill -9 -P $$

    log "Script interrupted. All processes terminated." "TERMINATE"

    wait  # Clean up any zombies

    # Remove files only after ensuring all processes are dead
    rm -f "$PID_FILE" "$LOCK_FILE" "$CLEANUP_IN_PROGRESS_FILE"

    # Exit with a non-zero status to indicate interruption
    exit 1
  fi

  # Normal cleanup actions (on EXIT)
  rm -f "$PID_FILE" "$LOCK_FILE" "$CLEANUP_IN_PROGRESS_FILE"
}

# Thread-safe update of the tracking file using flock
# Format: filename status hash_status
write_tracking_file() {
  local file="$1"
  local new_status="${2:-}"
  local new_hash_status="${3:-}"

  (
    flock -x 200

    # Pull existing line (if any)
    local existing_line
    existing_line=$(grep "^$file " "$TRACKING_FILE" 2>/dev/null || true)

    local old_status=""
    local old_hash=""
    if [[ -n "$existing_line" ]]; then
      old_status=$(echo "$existing_line" | awk '{print $2}')
      old_hash=$(echo "$existing_line" | awk '{print $3}')
    fi

    # If a line exists but no new import status was passed, keep the old import status
    # otherwise default to NOT_STARTED for brand-new file
    if [[ -z "$new_status" ]]; then
      if [[ -n "$old_status" ]]; then
        new_status="$old_status"
      else
        new_status="NOT_STARTED"
      fi
    fi

    # If a line exists but no new hash status was passed, keep the old hash status
    # otherwise default to HASH_UNVERIFIED for brand-new file
    if [[ -z "$new_hash_status" ]]; then
      if [[ -n "$old_hash" ]]; then
        new_hash_status="$old_hash"
      else
        new_hash_status="HASH_UNVERIFIED"
      fi
    fi

    # Remove any existing entry for this file
    grep -v "^$file " "$TRACKING_FILE" > "${TRACKING_FILE}.tmp" 2>/dev/null || true
    mv "${TRACKING_FILE}.tmp" "$TRACKING_FILE"

    # Add the updated line with the final status and hash
    echo "$file $new_status $new_hash_status" >> "$TRACKING_FILE"
  ) 200>"$LOCK_FILE"
}

# Read current import status of a file from tracking file
read_tracking_status() {
  local file="$1"
  grep "^$file " "$TRACKING_FILE" 2>/dev/null | cut -d' ' -f2-
}

# Find all .csv.gz files that need to be imported
collect_import_tasks() {
  enable_pipefail
  trap 'disable_pipefail' RETURN

  find "$IMPORT_DIR" -type f -name "*.csv.gz"
}

# Log file size, row count, or hash verification discrepancies into $DISCREPANCY_FILE
write_discrepancy() {
  local file="$1"
  local expected_count="$2"
  local actual_count="$3"

  # Only write if not already imported successfully and not in cleanup
  discrepancy_entry="$file: expected $expected_count, got $actual_count rows"
  if ! grep -q "^${file} IMPORTED$" "$TRACKING_FILE" 2>/dev/null && [[ ! -f "$CLEANUP_IN_PROGRESS_FILE" ]]; then
    echo "$discrepancy_entry" >> "$DISCREPANCY_FILE"
  fi
}

# Load PostgreSQL connection settings from $BOOTSTRAP_ENV_FILE (local var)
# Required vars: PGUSER, PGPASSWORD, PGDATABASE, PGHOST, PGPORT
source_bootstrap_env() {
  # Declare the bootstrap environment file as a local variable
  local BOOTSTRAP_ENV_FILE="bootstrap.env"

  if [[ -f "$BOOTSTRAP_ENV_FILE" ]]; then
    log "Sourcing $BOOTSTRAP_ENV_FILE to set environment variables."
    set -a  # Automatically export all variables
    source "$BOOTSTRAP_ENV_FILE"
    set +a
  else
    log "$BOOTSTRAP_ENV_FILE file not found." "ERROR"
    exit 1
  fi
}

# Parse $MANIFEST_FILE and prepare import tasks
# Format: table_name,file_name,row_count,file_size,blake3_hash
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

  # Check if the manifest file exists
  if [[ ! -f "$MANIFEST_FILE" ]]; then
    log "Manifest file '$MANIFEST_FILE' not found." "ERROR"
    exit 1
  fi

  # Validate file count
  log "Validating file count"
  # Count files in manifest (excluding header)
  manifest_file_count=$(tail -n +2 "$MANIFEST_FILE" | wc -l)

  # Count actual files in IMPORT_DIR (recursively)
  actual_file_count=$(find "$IMPORT_DIR" -type f -name "*.gz" | wc -l)

  if [[ "$manifest_file_count" != "$actual_file_count" ]]; then
    log "File count mismatch! Manifest: $manifest_file_count, Directory: $actual_file_count" "ERROR"
    exit 1
  fi
  log "File count validation successful, Manifest: $manifest_file_count, Directory: $actual_file_count"

  # Populate manifest_counts, manifest_tables, and run file validations
  while IFS=',' read -r filename expected_count expected_size expected_blake3_hash; do
    # Skip header line
    if [[ "$filename" == "filename" ]]; then
      continue
    fi

    # Build file_path based on directory layout
    # Large table part files reside in "$IMPORT_DIR/<table>/$filename"
    if [[ "$filename" =~ ^(.+)_part_ ]]; then
      table_prefix="${filename%%_part_*}"
      file_path="$IMPORT_DIR/$table_prefix/$filename"
    else
      # Small table files reside directly in $IMPORT_DIR/
      file_path="$IMPORT_DIR/$filename"
    fi

    if [[ -f "$file_path" ]]; then
      # Skip validation if file is already imported successfully
      if grep -q "^$file_path IMPORTED" "$TRACKING_FILE" 2>/dev/null; then
        continue
      fi

      # Skip non-data files and entries with 'N/A' expected count
      if [[ "$expected_count" != "N/A" ]]; then
        manifest_counts["$filename"]="$expected_count"

        # Extract table name only for data files
        if [[ "$filename" =~ ^([^/]+)_part_ ]]; then
          table="${BASH_REMATCH[1]}"
        elif [[ "$filename" =~ ^([^/]+)\.csv\.gz$ ]]; then
          table="${BASH_REMATCH[1]}"
        else
          log "Could not determine table name from filename: $filename" "ERROR"
          continue
        fi

        # Store table name in manifest_tables
        manifest_tables["$table"]=1
      fi
      manifest_sizes["$filename"]="$expected_size"
      manifest_hashes["$filename"]="$expected_blake3_hash"
    else
      missing_files+=("$filename")
    fi
  done < "$MANIFEST_FILE"

  MANIFEST_SIZES_SERIALIZED=$(declare -p manifest_sizes)
  MANIFEST_HASHES_SERIALIZED=$(declare -p manifest_hashes)
  export MANIFEST_SIZES_SERIALIZED MANIFEST_HASHES_SERIALIZED

  # Handle missing files
  if [[ ${#missing_files[@]} -gt 0 ]]; then
    log "The following files are listed in the manifest but are missing from the data directory:" "ERROR"
    for missing_file in "${missing_files[@]}"; do
      log "- $missing_file" "ERROR"
    done
    exit 1
  fi
}

# Validate a file's size and BLAKE3 hash against manifest values
validate_file() {
  local file="$1"
  local filename="$2"
  local failures=()

  if [[ ! -f "$file" ]]; then
    echo "file not found"
    return 1
  fi

  actual_size=$(stat -c%s "$file")
  expected_size="${manifest_sizes["$filename"]}"

  if [[ "$actual_size" != "$expected_size" ]]; then
    echo "size mismatch (expected: $expected_size bytes, actual: $actual_size bytes)"
    return 1
  fi

  actual_b3sum=$(b3sum --num-threads "$B3SUM_NUM_THREADS" --no-names "$file")
  expected_blake3_hash="${manifest_hashes["$filename"]}"

  if [[ "$actual_b3sum" != "$expected_blake3_hash" ]]; then
    echo "BLAKE3 hash mismatch (expected: $expected_blake3_hash, actual: $actual_b3sum)"
    return 1
  fi

  return 0
}

# Validate special files that are required before import can begin
validate_special_files() {
  enable_pipefail
  trap 'disable_pipefail' RETURN

  local special_files=("schema.sql.gz" "MIRRORNODE_VERSION.gz")
  local validation_failed=false
  local failures=()

  for filename in "${special_files[@]}"; do
    local file="$IMPORT_DIR/$filename"
    local validation_result

    validation_result=$(validate_file "$file" "$filename")
    if [[ $? -ne 0 ]]; then
      failures+=("$filename: $validation_result")
      validation_failed=true
      write_tracking_file "$file" "FAILED_VALIDATION"
    else
      log "Successfully validated special file: $filename"
      write_tracking_file "$file" "IMPORTED"
    fi
  done

  if [[ "$validation_failed" == "true" ]]; then
    log "Special file validation failed:" "ERROR"
    for failure in "${failures[@]}"; do
      log "  - $failure" "ERROR"
    done
    return 1
  fi

  return 0
}

# Import CSV file into database table with post import verification
# Verifies: file size, row count, and BLAKE3 hash
import_file() {
  # Don't start new imports during cleanup
  if [[ -f "$CLEANUP_IN_PROGRESS_FILE" ]]; then
    return 1
  fi

  enable_pipefail
  trap 'disable_pipefail' RETURN

  local file="$1"
  local table
  local filename
  local expected_count
  local actual_count
  local is_small_table
  local absolute_file
  local start_ts=""
  local end_ts=""
  local data_suffix=""
  local current_pid=$$

  # Declare manifest_counts, manifest_sizes, and manifest_hashes as associative arrays
  declare -A manifest_counts
  declare -A manifest_sizes
  declare -A manifest_hashes
  eval "$MANIFEST_COUNTS_SERIALIZED"
  eval "$MANIFEST_SIZES_SERIALIZED"
  eval "$MANIFEST_HASHES_SERIALIZED"

  # Get filename and ensure absolute paths
  absolute_file="$(realpath "$file")"
  filename=$(basename "$absolute_file")
  expected_count="${manifest_counts[$filename]}"

  # Mark file as being processed
  processing_files["$file"]=1

  # Perform BLAKE3 and file-size validations
  if ! grep -q "^$file IMPORTED" "$TRACKING_FILE" 2>/dev/null; then
    local validation_result
    validation_result=$(validate_file "$file" "$filename")
    if [[ $? -ne 0 ]]; then
      if [[ ! -f "$CLEANUP_IN_PROGRESS_FILE" ]]; then
        log "Validation failed for $filename: $validation_result" "ERROR"
      fi
      write_tracking_file "$file" "FAILED_VALIDATION"
      return 1
    fi

    log "Successfully validated file-size and BLAKE3 hash for $filename"
    current_status=$(read_tracking_status "$file")
    if [[ -n "$current_status" ]]; then
      status=$(echo "$current_status" | cut -d' ' -f1)
      write_tracking_file "$file" "$status" "HASH_VERIFIED"
    else
      write_tracking_file "$file" "NOT_STARTED" "HASH_VERIFIED"
    fi
  fi

  # Skip non-table files after validation
  if [[ "$filename" == "MIRRORNODE_VERSION.gz" || "$filename" == "schema.sql.gz" ]]; then
    log "Successfully validated non-table file: $filename"
    write_tracking_file "$file" "IMPORTED"
    unset processing_files["$file"]
    return 0
  fi

  # Determine if this is a small table by checking filename pattern
  is_small_table=false  # default to large table part
  if [[ ! "$filename" =~ ^(.+)_part_ ]]; then
    is_small_table=true  # small table
    table=$(basename "$file" .csv.gz)
  else
    # Extract table name - everything before _part_ (can contain underscores)
    table="${filename%%_part_*}"

    # Get everything after _part_ and split into components
    part_suffix="${filename#*_part_}"  # Remove everything up to and including _part_
    start_ts=$(echo "$part_suffix" | cut -d'_' -f2)    # Second field after _part_
    end_ts=$(echo "$part_suffix" | cut -d'_' -f3)      # Third field after _part_
    data_suffix=$(echo "$part_suffix" | cut -d'_' -f4 | cut -d'.' -f1)  # Fourth field, remove extension
  fi

  # Log import start and update status
  log "Importing into table $table from $filename, PID: $current_pid"
  write_tracking_file "$file" "IN_PROGRESS"

  # Execute the import within a transaction
  if ! { $DECOMPRESS_TOOL $DECOMPRESS_FLAGS "$file" 2>/dev/null | PGAPPNAME="bootstrap_$current_pid" psql -q -v ON_ERROR_STOP=1 --single-transaction -c "COPY $table FROM STDIN WITH CSV HEADER;" 2>/dev/null; }; then
    if [[ ! -f "$CLEANUP_IN_PROGRESS_FILE" ]]; then
      log "Error importing data for $file" "ERROR"
    fi
    write_tracking_file "$file" "FAILED_TO_IMPORT"
    return 1
  fi

  # Skip verification if no expected count
  if [[ -z "$expected_count" || "$expected_count" == "N/A" ]]; then
    log "No expected row count for $filename in manifest, skipping verification."
    write_tracking_file "$file" "IMPORTED"
    return 0
  fi

  # Row count verification based on table type
  if [ "$is_small_table" = true ]; then
    if ! actual_count=$(psql -qt -v ON_ERROR_STOP=1 -c "SELECT COUNT(*) FROM \"$table\";" | xargs); then
      if [[ ! -f "$CLEANUP_IN_PROGRESS_FILE" ]]; then
        log "Error executing count query for $file" "ERROR"
      fi
      write_tracking_file "$file" "FAILED_TO_IMPORT"
      return 1
    fi
  else
    # Use the previously captured timestamps for large table
    if [[ -n "$start_ts" && -n "$end_ts" ]]; then
      if ! actual_count=$(psql -qt -v ON_ERROR_STOP=1 -c "SELECT COUNT(*) FROM \"$table\" WHERE consensus_timestamp BETWEEN $start_ts AND $end_ts;" | xargs); then
        if [[ ! -f "$CLEANUP_IN_PROGRESS_FILE" ]]; then
          log "Error executing count query for $file" "ERROR"
        fi
        write_tracking_file "$file" "FAILED_TO_IMPORT"
        return 1
      fi
    else
      if [[ ! -f "$CLEANUP_IN_PROGRESS_FILE" ]]; then
        log "Error: Missing timestamps for large table file $filename" "ERROR"
      fi
      write_tracking_file "$file" "FAILED_TO_IMPORT"
      return 1
    fi
  fi

  # Verify the count matches expected
  if [[ "$actual_count" != "$expected_count" ]]; then
    if [[ ! -f "$CLEANUP_IN_PROGRESS_FILE" ]]; then
      log "Row count mismatch for $file. Expected: $expected_count, Actual: $actual_count" "ERROR"
    fi
    write_tracking_file "$file" "FAILED_TO_IMPORT"
    write_discrepancy "$file" "$expected_count" "$actual_count"
    return 1
  else
    log "Row count verified, successfully imported $file"
  fi

  # Remove file from processing list when done
  unset processing_files["$file"]
  write_tracking_file "$file" "IMPORTED"
  return 0
}

# Initialize an empty database schema using init.sh
initialize_database() {
  enable_pipefail
  trap 'disable_pipefail' RETURN

  # Reconstruct manifest_tables
  declare -A manifest_tables
  eval "$MANIFEST_TABLES_SERIALIZED"

  # Check for schema.sql
  if [[ ! -f "$IMPORT_DIR/schema.sql" ]]; then
    log "schema.sql not found in $IMPORT_DIR." "ERROR"
    exit 1
  fi

  # Construct the URL for init.sh
  INIT_SH_URL="https://raw.githubusercontent.com/hiero-ledger/hiero-mirror-node/refs/heads/main/hedera-mirror-importer/src/main/resources/db/scripts/init.sh"

  # Download init.sh
  log "Downloading init.sh from $INIT_SH_URL"

  if curl -fSLs -o "init.sh" "$INIT_SH_URL"; then
    log "Successfully downloaded init.sh"
  else
    log "Failed to download init.sh" "ERROR"
    exit 1
  fi

  # Make init.sh executable
  chmod +x init.sh

  # Run init.sh to initialize the database and capture its output
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

  log "Updated PostgreSQL environment variables to connect to 'mirror_node' database as user 'mirror_node'"

  # Execute schema.sql
  log "Executing schema.sql from $IMPORT_DIR"
  if psql -v ON_ERROR_STOP=1 -f "$IMPORT_DIR/schema.sql" >> "$LOG_FILE" 2>&1; then
    log "schema.sql executed successfully"
  else
    log "Failed to execute schema.sql. Check $LOG_FILE for details." "ERROR"
    exit 1
  fi

  # Check that each table exists in the database
  # Test database connectivity
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

    # Avoid duplicate checks
    if [[ -n "${checked_tables_map["$table"]:-}" ]]; then
      log "Table $table has already been checked. Skipping."
      continue
    fi
    checked_tables_map["$table"]=1

    # Check if the table exists in the database, with a timeout
    if ! timeout 10 psql -v ON_ERROR_STOP=1 -qt -c "SELECT 1 FROM pg_class WHERE relname = '$table' AND relnamespace = 'public'::regnamespace;" | grep -q 1; then
      missing_tables+=("$table")
      log "$table missing from database" "ERROR"
    else
      log "$table exists in the database"
    fi
  done

  # If any tables are missing, report and exit
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
}

####################################
# Execution
####################################

# Trap SIGINT and SIGTERM for graceful shutdown
trap 'cleanup INT' SIGINT
trap 'cleanup TERM' SIGTERM
# Trap EXIT for normal cleanup without interruption logging
trap 'cleanup EXIT' EXIT

# Perform the Bash version check
check_bash_version

# Log the Process Group ID
PGID=$(ps -o pgid= $$ | tr -d ' ')
log "Script Process Group ID: $PGID"

# Display help if no arguments are provided
if [[ $# -eq 0 ]]; then
  echo "No arguments provided. Use --help or -h for usage information."
  show_help
  exit 1
fi

# Parse help first, then options
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

# Assign script arguments
DB_CPU_CORES="$1"
shift

# Process additional options
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

# Check if required arguments are supplied and valid
if [[ -z "$DB_CPU_CORES" || -z "$IMPORT_DIR" ]]; then
    log "Missing required arguments. Use --help or -h for usage information" "ERROR"
    exit 1
fi

# Validate DB_CPU_CORES is a positive integer
if ! [[ "$DB_CPU_CORES" =~ ^[1-9][0-9]*$ ]]; then
    log "DB_CPU_CORES must be a positive integer" "ERROR"
    exit 1
fi

# Convert IMPORT_DIR to an absolute path
IMPORT_DIR="$(realpath "$IMPORT_DIR")"

# Calculate available CPU cores
AVAILABLE_CORES=$(($(nproc) - 1))        # Leave one core free for the local system
DB_AVAILABLE_CORES=$((DB_CPU_CORES - 1)) # Leave one core free for the DB instance
if (( DB_AVAILABLE_CORES <= 0 )); then
  DB_AVAILABLE_CORES=1  # Minimum 1 core
fi
log "DB_AVAILABLE_CORES set to: $DB_AVAILABLE_CORES"

# Source bootstrap environment variables early
source_bootstrap_env

# Check for all required tools before proceeding
if ! check_required_tools; then
    exit 1
fi

# Set file paths
if [[ -n "$USE_FULL_DB" ]]; then
    MANIFEST_FILE="${IMPORT_DIR}/manifest.csv"
else
    MANIFEST_FILE="${IMPORT_DIR}/manifest.minimal.csv"
fi
MIRRORNODE_VERSION_FILE="$IMPORT_DIR/MIRRORNODE_VERSION"
log "Using manifest file: $MANIFEST_FILE"

# Check if IMPORT_DIR exists and is a directory
if [[ ! -d "$IMPORT_DIR" ]]; then
  log "IMPORT_DIR '$IMPORT_DIR' does not exist or is not a directory" "ERROR"
  exit 1
fi

# Calculate optimal number of parallel jobs
if [[ $AVAILABLE_CORES -lt $DB_AVAILABLE_CORES ]]; then
  MAX_JOBS="$AVAILABLE_CORES"
else
  MAX_JOBS="$DB_AVAILABLE_CORES"
fi

# Process the manifest and check for missing files
process_manifest

# Validate special files first
if ! validate_special_files; then
  exit 1
fi

# Decompress schema.sql and MIRRORNODE_VERSION
for file in "$IMPORT_DIR/schema.sql.gz" "$IMPORT_DIR/MIRRORNODE_VERSION.gz"; do
  if ! $DECOMPRESS_TOOL ${DECOMPRESS_FLAGS/-c/-k} -f "$file" 2>/dev/null; then
    log "Error decompressing $file using $DECOMPRESS_TOOL" "ERROR"
    exit 1
  fi
done

# Serialize manifest data for subshell access
MANIFEST_COUNTS_SERIALIZED=$(declare -p manifest_counts)
MANIFEST_TABLES_SERIALIZED=$(declare -p manifest_tables)

# Extract and validate mirrornode version
if [[ -f "$MIRRORNODE_VERSION_FILE" ]]; then
  MIRRORNODE_VERSION=$(tr -d '[:space:]' < "$MIRRORNODE_VERSION_FILE")
  log "Compatible Mirrornode version: $MIRRORNODE_VERSION"
else
  log "MIRRORNODE_VERSION file not found in $IMPORT_DIR." "ERROR"
  exit 1
fi

# Log the start of the import process
log "Starting DB import."

# Initialize the database unless the flag file exists
if [[ ! -f "$DB_SKIP_FLAG_FILE" ]]; then
  initialize_database
  touch "$DB_SKIP_FLAG_FILE" # Create a flag to skip subsequent runs from running db init after it succeeded once
else
  # Set PostgreSQL environment variables
  export PGUSER="mirror_node"
  export PGDATABASE="mirror_node"
  export PGPASSWORD="$OWNER_PASSWORD"

  log "Set PGUSER, PGDATABASE, and PGPASSWORD for PostgreSQL."

  # Test database connectivity
  if ! psql -v ON_ERROR_STOP=1 -c '\q' >/dev/null 2>&1; then
    log "Database is not initialized. Cannot skip database initialization." "ERROR"
    exit 1
  fi
  log "Database is already initialized, skipping initialization."
fi

# Get the list of files to import
mapfile -t files < <(collect_import_tasks)

# Initialize the tracking file with all files as NOT_STARTED and HASH_UNVERIFIED
(
  flock -x 200
  for file in "${files[@]}"; do
    # Only add if not already in tracking file
    if ! grep -q "^$file " "$TRACKING_FILE" 2>/dev/null; then
      echo "$file NOT_STARTED HASH_UNVERIFIED" >> "$TRACKING_FILE"
    fi
  done
) 200>"$LOCK_FILE"

# Initialize variables for background processes
overall_success=true
failed_imports=0

# Export required functions and variables for subshell usage
export -f \
  log show_help check_bash_version check_required_tools \
  determine_decompression_tool kill_descendants cleanup write_tracking_file read_tracking_status \
  collect_import_tasks write_discrepancy source_bootstrap_env process_manifest validate_file \
  validate_special_files initialize_database import_file

export \
  DECOMPRESS_TOOL DECOMPRESS_FLAGS BOOTSTRAP_ENV_FILE DISCREPANCY_FILE \
  IMPORT_DIR LOG_FILE MANIFEST_FILE TRACKING_FILE LOCK_FILE MAX_JOBS

# Process files in parallel up to $MAX_JOBS
for file in "${files[@]}"; do
  # Check if the file has already been imported
  if grep -q "^$file IMPORTED" "$TRACKING_FILE" 2>/dev/null; then
    log "Skipping processing of already imported file: $file"
    continue
  fi

  # Wait if $MAX_JOBS are already running
  while [[ $(jobs -rp | wc -l) -ge $MAX_JOBS ]]; do
    # Wait for any job to finish
    if ! wait -n; then
      overall_success=false
      log "One or more import jobs failed" "ERROR"
      ((failed_imports++))
    fi
  done

  # Start import in background and capture its PID
  import_file "$file" &
  pids+=($!)
done

# Wait for all remaining jobs to finish
wait

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

# Exit with the appropriate status
exit $((1 - overall_success))