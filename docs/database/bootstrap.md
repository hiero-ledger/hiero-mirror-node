# Database Bootstrap Guide

This guide provides step-by-step instructions for setting up a fresh PostgreSQL database and importing Mirror Node data into it using the `bootstrap` Go binary. The process involves initializing the database, configuring environment variables, and running the import. The data import is a long-running process, so it's important to ensure it continues running even if your SSH session is terminated.

---

## Table of Contents

- [Prerequisites](#prerequisites)
  - [1. Sizing the Bootstrap Machine](#1-sizing-the-bootstrap-machine)
- [Database Initialization and Data Import](#database-initialization-and-data-import)
  - [1. Download the Required Files](#1-download-the-required-files)
  - [2. Edit the `bootstrap.env` Configuration File](#2-edit-the-bootstrapenv-configuration-file)
  - [3. Download the Database Export Data](#3-download-the-database-export-data)
    - [3.1. Set Your Default GCP Project](#31-set-your-default-gcp-project)
    - [3.2. List Available Versions](#32-list-available-versions)
    - [3.3. Select a Version](#33-select-a-version)
    - [3.4. Download the Data](#34-download-the-data)
      - [Download Minimal DB Data Files](#download-minimal-db-data-files)
      - [Download Full DB Data Files](#download-full-db-data-files)
  - [4. Check Version Compatibility](#4-check-version-compatibility)
  - [5. Initialize the Database](#5-initialize-the-database)
  - [6. Run the Import](#6-run-the-import)
  - [7. Monitoring and Managing the Import Process](#7-monitoring-and-managing-the-import-process)
    - [7.1. Monitoring the Import Process](#71-monitoring-the-import-process)
    - [7.2. Stopping the Import](#72-stopping-the-import)
    - [7.3. Resuming the Import Process](#73-resuming-the-import-process)
    - [7.4. Start the Mirror Node Importer](#74-start-the-mirror-node-importer)
- [Handling Failed Imports](#handling-failed-imports)
- [Additional Notes](#additional-notes)
- [Troubleshooting](#troubleshooting)

---

## Prerequisites

1. Access to a modern Linux or macOS machine where you can run the bootstrap binary and connect to the PostgreSQL database.
2. **PostgreSQL 16** installed and running.
3. Ensure the following tools are installed on your machine:

   - `psql` (PostgreSQL client)
   - `jq` (optional, for parsing the tracking file)

4. Install the [Google Cloud SDK](https://cloud.google.com/sdk/docs/install), then authenticate:

   ```bash
   gcloud auth login
   ```

5. A Google Cloud Platform (GCP) account with a valid billing account attached (required for downloading data from a Requester Pays bucket). For detailed instructions on obtaining the necessary GCP information, refer to the [documentation](https://docs.hedera.com/hedera/core-concepts/mirror-nodes/run-your-own-beta-mirror-node/run-your-own-mirror-node-gcs#id-1.-obtain-google-cloud-platform-requester-pay-information).

   ### 1. Sizing the Bootstrap Machine

   The `bootstrap` binary processes up to `--jobs` (default: 8) data files concurrently. Each file uses `DECOMPRESSOR_THREADS` (default: 4) goroutines for parallel gzip decompression.

   - **CPU Cores:** Recommended formula: `MAX_JOBS + DECOMPRESSOR_THREADS + 2`
     - _Example:_ With defaults (8 jobs, 4 decompressor threads): `8 + 4 + 2 = 14` CPU cores
     - For maximum throughput on large machines: up to `MAX_JOBS × DECOMPRESSOR_THREADS` cores
   - **RAM:** Allocate 128-256MB per parallel job
     - _Example:_ For 8 jobs, aim for 2GB to 4GB of RAM.

   > [!NOTE]
   > For optimal performance and resource isolation, it is strongly recommended to run `bootstrap` on a separate machine from the PostgreSQL database server. This prevents contention for CPU, RAM, and I/O resources between the import process and the database itself.
   >
   > Additionally, ensure the database server has adequate resources for the import: allocate at least `MAX_JOBS + 2` CPU threads and sufficient RAM to handle concurrent write operations.

---

## Database Initialization and Data Import

### 1. Build `bootstrap` from Source

**Ensure you have Go 1.25 or later installed:**

```bash
# Check if Go is installed and verify version
go version
```

If Go is not installed or the version is older than 1.25, install or upgrade:

- **Linux (Ubuntu/Debian):**
  ```bash
  # Remove old Go installation (if exists)
  sudo rm -rf /usr/local/go

  # Download and install Go 1.25+ (adjust version as needed)
  wget https://go.dev/dl/go1.25.0.linux-amd64.tar.gz
  sudo tar -C /usr/local -xzf go1.25.0.linux-amd64.tar.gz
  rm go1.25.0.linux-amd64.tar.gz

  # Add to PATH (add to ~/.bashrc or ~/.profile for persistence)
  export PATH=$PATH:/usr/local/go/bin
  ```

- **macOS:**
  ```bash
  # Using Homebrew (installs or upgrades to latest version)
  brew install go || brew upgrade go

  # Or download from https://go.dev/dl/
  ```

After installation, verify:

```bash
go version
```

**Download the bootstrap source code:**

```bash
# Download the source tarball
wget https://github.com/hiero-ledger/hiero-mirror-node/archive/refs/heads/main.tar.gz

# Extract only the bootstrap directory
tar xz --strip=2 -f main.tar.gz hiero-mirror-node-main/tools/bootstrap

# Clean up
rm main.tar.gz

# Navigate to the bootstrap directory
cd bootstrap
```

**Build the binary:**

Install dependencies:

```bash
go mod tidy
```

Build for your target platform:

```bash
# Current platform (build and run locally)
go build -o bootstrap ./cmd/mirrornode-bootstrap/

# Linux x86_64
GOOS=linux GOARCH=amd64 go build -o bootstrap ./cmd/mirrornode-bootstrap/

# macOS x86_64 (Intel)
GOOS=darwin GOARCH=amd64 go build -o bootstrap ./cmd/mirrornode-bootstrap/

# macOS ARM64 (Apple Silicon)
GOOS=darwin GOARCH=arm64 go build -o bootstrap ./cmd/mirrornode-bootstrap/
```

Verify the binary (if built for current platform):

```bash
./bootstrap --help
```

> [!NOTE]
> The `bootstrap.env` configuration file is included in the directory. You can now move both the `bootstrap` binary and `bootstrap.env` file to your preferred working directory, or continue working in the current directory.

### 2. Edit the `bootstrap.env` Configuration File

Edit the `bootstrap.env` file to set your own credentials and passwords for database users during initialization.

**Instructions:**

- **Set PostgreSQL Environment Variables:**

  ```bash
  # PostgreSQL environment variables
  export PGUSER="postgres"
  export PGPASSWORD="your_postgres_password"
  export PGDATABASE="postgres"
  export PGHOST="127.0.0.1"
  export PGPORT="5432"
  ```

  - Replace `your_postgres_password` with the password for the PostgreSQL superuser (`postgres`).
  - `PGHOST` should be set to the IP address or hostname of your PostgreSQL server.

- **Set the `IS_GCP_CLOUD_SQL` variable to `true` if you are using a GCP Cloud SQL database:**

  ```bash
  # Is the DB a GCP Cloud SQL instance?
  export IS_GCP_CLOUD_SQL="true"
  ```

  - Otherwise, leave it as `false`.

- **Set Database User Passwords:**

  ```bash
  # Set DB users' passwords
  export GRAPHQL_PASSWORD="SET_PASSWORD"
  export GRPC_PASSWORD="SET_PASSWORD"
  export IMPORTER_PASSWORD="SET_PASSWORD"
  export OWNER_PASSWORD="SET_PASSWORD"
  export REST_PASSWORD="SET_PASSWORD"
  export REST_JAVA_PASSWORD="SET_PASSWORD"
  export ROSETTA_PASSWORD="SET_PASSWORD"
  export WEB3_PASSWORD="SET_PASSWORD"
  ```

  - Replace each `SET_PASSWORD` with a strong, unique password for each respective database user.

- **(Optional) Configure Performance Settings:**

  ```bash
  # Import performance settings (optional - defaults shown)
  export MAX_JOBS="8"                    # Number of parallel import workers
  export DECOMPRESSOR_THREADS="4"        # Goroutines per file for parallel gzip decompression
  ```

  - `MAX_JOBS`: Number of files to import in parallel (see [Sizing the Bootstrap Machine](#1-sizing-the-bootstrap-machine))
  - `DECOMPRESSOR_THREADS`: Number of goroutines for parallel gzip decompression per file

- **Save and Secure the `bootstrap.env` File:**

  - After editing, save the file.
  - Ensure that the `bootstrap.env` file is secured and not accessible to unauthorized users, as it contains sensitive information.

    ```bash
    chmod 600 bootstrap.env
    ```

### 3. Download the Database Export Data

The Mirror Node database export data is available in a Google Cloud Storage (GCS) bucket:

- **Bucket URL:** [mirrornode-db-export](https://console.cloud.google.com/storage/browser/mirrornode-db-export)

**Important Notes:**

- The bucket is **read-only** to the public.
- It is configured as **Requester Pays**, meaning you need a GCP account with a valid billing account attached to download the data. For detailed instructions, refer to [Hedera's documentation on GCS](https://docs.hedera.com/hedera/core-concepts/mirror-nodes/run-your-own-beta-mirror-node/run-your-own-mirror-node-gcs#id-1.-obtain-google-cloud-platform-requester-pay-information).
- You will be billed for the data transfer fees incurred during the download.

#### 3.1. Set Your Default GCP Project

```bash
gcloud config set project YOUR_GCP_PROJECT_ID
```

- Replace YOUR_GCP_PROJECT_ID with your actual GCP project ID.

#### 3.2. List Available Versions

To see the available versions of the database export, list the contents of the bucket:

```bash
gcloud storage ls gs://mirrornode-db-export/
```

This will display the available version directories.

#### 3.3. Select a Version

- **Select the latest available version** from the output of the previous command.

  - Legacy versions will be removed from the bucket shortly after a newer version's export data becomes available.

- **Ensure Compatibility:**

  - The mirror node must be initially deployed and started against the same version of the database export.
  - Be aware that using mismatched versions may lead to compatibility issues and schema mismatches.

#### 3.4. Download the Data

Choose one of the following download options based on your needs:

##### Download Minimal DB Data Files

Create a directory and download only the minimal database files:

```bash
VERSION="<VERSION_NUMBER>"
DOWNLOAD_DIR="</path/to/db_export>"
mkdir -p "$DOWNLOAD_DIR"
export CLOUDSDK_STORAGE_SLICED_OBJECT_DOWNLOAD_MAX_COMPONENTS=1 && \
gcloud storage rsync -r -x ".*_atma\.csv\.gz$" "gs://mirrornode-db-export/$VERSION/" "$DOWNLOAD_DIR/"
```

##### Download Full DB Data Files

Create a directory and download all files and subdirectories for the selected version:

```bash
VERSION="<VERSION_NUMBER>"
DOWNLOAD_DIR="</path/to/db_export>"
mkdir -p "$DOWNLOAD_DIR"
export CLOUDSDK_STORAGE_SLICED_OBJECT_DOWNLOAD_MAX_COMPONENTS=1 && \
gcloud storage rsync -r "gs://mirrornode-db-export/$VERSION/" "$DOWNLOAD_DIR/"
```

For both options:

- Replace `</path/to/db_export>` with your actual download path.
- Replace `<VERSION_NUMBER>` with the version you selected (e.g., `0.111.0`).
- Ensure all files and subdirectories are downloaded into this single parent directory.

### 4. Check Version Compatibility

After downloading the data, it's crucial to ensure version compatibility between the database export and the Mirror Node you're setting up.

**Steps:**

1. **Locate the `MIRRORNODE_VERSION.gz` File:**

   - The downloaded data should include a file named `MIRRORNODE_VERSION.gz` in the root of the `/path/to/db_export` directory.

2. **Check the Mirror Node Version:**

   ```bash
   zcat /path/to/db_export/MIRRORNODE_VERSION.gz
   ```

3. **Ensure Version Compatibility:**

   - The version number in the `MIRRORNODE_VERSION.gz` file should match the name of the directory from which you downloaded the data, and should also be the version of the Mirror Node you intend to run using this export's data.

### 5. Initialize the Database

The `bootstrap init` command creates the database, roles, and permissions, validates the special files (`MIRRORNODE_VERSION.gz` and `schema.sql.gz`), and executes the schema.

**Instructions:**

```bash
./bootstrap init \
  --config bootstrap.env \
  --data-dir /path/to/db_export
```

**Flags:**
- `-c, --config`: Path to the `bootstrap.env` configuration file
- `-d, --data-dir`: Directory containing the downloaded data (including `schema.sql.gz`)
- `-s, --schema`: (Optional) Direct path to `schema.sql` file, overrides `--data-dir`

**Notes:**

- The init command creates a `bootstrap-logs/SKIP_DB_INIT` flag file after successful initialization.
- On subsequent runs, if `bootstrap-logs/SKIP_DB_INIT` exists, initialization is skipped.
- To force re-initialization, delete the flag file:

  ```bash
  rm -f bootstrap-logs/SKIP_DB_INIT
  ```

### 6. Run the Import

The `bootstrap import` command imports all data files into PostgreSQL using parallel streaming COPY operations. All parallelism is managed internally via Go goroutines.

**Option A: Interactive Mode (stay connected)**

Run directly with output to terminal:

```bash
./bootstrap import \
  --config bootstrap.env \
  --data-dir /path/to/db_export \
  --manifest /path/to/db_export/manifest.csv \
  --jobs 8
```

**Option B: Background Mode (SSH-safe)**

Run in the background so it continues if your SSH session disconnects:

```bash
nohup ./bootstrap import \
  --config bootstrap.env \
  --data-dir /path/to/db_export \
  --manifest /path/to/db_export/manifest.csv \
  --jobs 8 \
  > bootstrap-logs/bootstrap.log 2>&1 &
```

**Flags:**
- `-c, --config`: Path to the `bootstrap.env` configuration file
- `-d, --data-dir`: Directory containing the gzipped CSV data files (required)
- `-m, --manifest`: Path to the `manifest.csv` file (required)
- `-j, --jobs`: Number of parallel import jobs (default: 8)

**Note:** For live progress monitoring, use the separate `watch` command (see section 7.1 below).

**Files Created:**

The import process creates several files in the `bootstrap-logs/` directory:

- `bootstrap-logs/bootstrap.log`: Main log file for all operations.
- `bootstrap-logs/bootstrap.pid`: Process ID of the running import (for process management).
- `bootstrap-logs/tracking.json`: JSON file tracking the import status of each file.
- `bootstrap-logs/progress.txt`: Periodic progress snapshots (updated during import).
- `bootstrap-logs/bootstrap_discrepancies.log`: Records any row count mismatches (only created if issues are found).
- `bootstrap-logs/SKIP_DB_INIT`: Flag file indicating database has been initialized.

**Verify the Import is Running (Background Mode):**

```bash
# Check if process is running
ps -p $(cat bootstrap-logs/bootstrap.pid)

# Watch the log file
tail -f bootstrap-logs/bootstrap.log

# Monitor live progress (in a separate terminal)
./bootstrap watch -c bootstrap.env -m /path/to/db_export/manifest.csv -d /path/to/db_export
```

### 7. Monitoring and Managing the Import Process

#### **7.1. Monitoring the Import Process:**

- **Live Progress Display (Recommended):**

  Run the `watch` command in a separate terminal to see real-time progress:

  ```bash
  ./bootstrap watch \
    -c bootstrap.env \
    -m /path/to/db_export/manifest.csv \
    -d /path/to/db_export
  ```

  Flags:
  - `-c, --config`: Path to bootstrap.env configuration file
  - `-m, --manifest`: Path to manifest.csv file (enables row count and percentage display)
  - `-d, --data-dir`: Directory containing data files
  - `-i, --interval`: Refresh interval in seconds (default: 1)

  This displays a live-updating table showing:
  - `Filename`: The data file being imported
  - `Rows/Total`: Current rows processed / expected total (requires `--manifest`)
  - `%`: Completion percentage
  - `Rate`: Import speed in rows per second

  Press `Ctrl+C` to stop watching.

- **Check the Main Log File:**

  ```bash
  tail -f bootstrap-logs/bootstrap.log
  ```

  - The binary logs all activity including file imports, validation results, and errors.
  - Files are processed in parallel, so log entries may appear in an arbitrary order.

- **Quick Status Check:**

  ```bash
  ./bootstrap status -c bootstrap.env
  ```

  Displays a summary of import progress from the tracking file:
  - `Imported`: Files successfully imported
  - `In Progress`: Files currently being imported
  - `Failed`: Files that failed to import
  - `Not Started`: Files not yet processed

- **Check the Tracking File:**

  ```bash
  # View formatted JSON
  cat bootstrap-logs/tracking.json | jq .

  # Count total files
  jq 'keys | length' bootstrap-logs/tracking.json

  # Show only IMPORTED files
  jq 'to_entries | .[] | select(.value.status == "IMPORTED") | .key' bootstrap-logs/tracking.json

  # Show files NOT imported (failed or in progress)
  jq 'to_entries | .[] | select(.value.status != "IMPORTED")' bootstrap-logs/tracking.json

  # Count by status
  jq '[.[] | {status: .status}] | group_by(.status) | map({status: .[0].status, count: length})' bootstrap-logs/tracking.json
  ```

  - This JSON file tracks the status of each file being imported.
  - Each entry contains:
    - `status`: Import status
      - `NOT_STARTED`: File has not begun processing.
      - `IN_PROGRESS`: File is currently being validated or imported.
      - `IMPORTED`: File was successfully validated and imported.
      - `FAILED_VALIDATION`: File failed size or hash check.
      - `FAILED_TO_IMPORT`: File import process failed.
    - `hash_status`: Hash verification status
      - `HASH_UNVERIFIED`: BLAKE3 hash has not been verified yet.
      - `HASH_VERIFIED`: BLAKE3 hash verification passed.

#### **7.2. Stopping the Import**

If you need to stop the import before it completes:

1. **Gracefully Terminate the Process:**

   ```bash
   kill -TERM $(cat bootstrap-logs/bootstrap.pid)
   ```

   - Sends the `SIGTERM` signal to the import process.
   - Allows the binary to perform cleanup (cancel in-flight operations) and exit gracefully.

2. **If the Process Doesn't Stop, Force Termination:**

   ```bash
   kill -KILL $(cat bootstrap-logs/bootstrap.pid)
   ```

   - Sends the `SIGKILL` signal for immediate termination.
   - **Cleanup might not complete successfully.** This is generally discouraged unless graceful termination does not work.

#### **7.3. Resuming the Import Process**

Simply re-run the import command:

```bash
# Interactive mode
./bootstrap import \
  --config bootstrap.env \
  --data-dir /path/to/db_export \
  --manifest /path/to/db_export/manifest.csv \
  --jobs 8

# Or background mode
nohup ./bootstrap import \
  --config bootstrap.env \
  --data-dir /path/to/db_export \
  --manifest /path/to/db_export/manifest.csv \
  --jobs 8 \
  >> bootstrap-logs/bootstrap.log 2>&1 &
```

- The import automatically resumes where it left off.
- Files marked as `IMPORTED` in `bootstrap-logs/tracking.json` are skipped.
- Files left in `IN_PROGRESS`, `FAILED_TO_IMPORT`, or `FAILED_VALIDATION` from the previous run are reset to `NOT_STARTED` on startup. Each file handles its own data cleanup (truncation or targeted delete) during import. This reset is logged for each file.

#### **7.4. Start the Mirror Node Importer**

Once the bootstrap process completes without errors:

1. Check `bootstrap-logs/bootstrap.log` for a success message.
2. Run `./bootstrap status -c bootstrap.env` and verify all files show as `Imported` with zero failures.
3. You may now start the Mirror Node Importer service.

---

## Handling Failed Imports

During the import process, the binary tracks the status of each file in `bootstrap-logs/tracking.json`. If any files end with `FAILED_VALIDATION` or `FAILED_TO_IMPORT`, the import will exit with an error status.

**Example of `bootstrap-logs/tracking.json`:**

```json
{
  "account_balance_p2024_01.csv.gz": {"status": "IMPORTED", "hash_status": "HASH_VERIFIED"},
  "transaction_p2019_08.csv.gz": {"status": "FAILED_VALIDATION", "hash_status": "HASH_UNVERIFIED"},
  "transaction_p2019_09.csv.gz": {"status": "FAILED_TO_IMPORT", "hash_status": "HASH_VERIFIED"},
  "token_balance_p2024_01.csv.gz": {"status": "IMPORTED", "hash_status": "HASH_VERIFIED"},
  "contract_result_p2024_01.csv.gz": {"status": "NOT_STARTED", "hash_status": "HASH_UNVERIFIED"}
}
```

> **Note:** Special files (`schema.sql.gz` and `MIRRORNODE_VERSION.gz`) are handled during `init`, not `import`, and are not tracked in this file.

**Notes on Data Consistency:**

- **Automatic Cleanup and Retry:** When you re-run the import command, files left in `IN_PROGRESS`, `FAILED_TO_IMPORT`, or `FAILED_VALIDATION` are automatically cleaned up (truncated and reset to `NOT_STARTED`), then re-imported along with any remaining `NOT_STARTED` files.
- **Data Integrity:** Each file import uses a single PostgreSQL transaction, ensuring no partial data is committed in case of failure. Hash and size validation prevent corrupted data files from being imported.

---

## Additional Notes

- **System Resources:**
  - Adjust the number of parallel jobs (`--jobs`) based on your system's capabilities.
  - Monitor system resources (CPU, memory, I/O) on both the bootstrap machine and the database server during the import process.
- **Security Considerations:**
  - Secure your `bootstrap.env` file and any other files containing sensitive information.
- **Debug Mode:**
  - Set `DEBUG_MODE=true` environment variable for verbose logging:
    ```bash
    DEBUG_MODE=true ./bootstrap import ...
    ```

---

## Troubleshooting

- **Connection Errors:**
  - Confirm that `PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD`, `PGDATABASE` in `bootstrap.env` are correctly set.
  - Ensure that the database server allows connections from your machine (check `pg_hba.conf` and firewall rules).
  - Verify that the database port (`PGPORT`) is correct and accessible.
- **Import Failures:**
  - Review `bootstrap-logs/bootstrap.log` for detailed error messages.
  - Check `bootstrap-logs/tracking.json` to identify which files failed validation or import.
  - Check `bootstrap-logs/bootstrap_discrepancies.log` for specific row count mismatches.
  - Re-run the import command to retry failed files.
- **Permission Denied Errors:**
  - Ensure that the user specified in `PGUSER` has superuser privileges or sufficient permissions to create databases and roles.
  - Verify that file system permissions allow the binary to read the data directory and write to the `bootstrap-logs/` directory.
- **Environment Variable Issues:**
  - Double-check that all required variables in `bootstrap.env` are correctly set and exported.
  - Ensure there are no typos or missing variables.
- **Process Does Not Continue After SSH Disconnect:**
  - Ensure you used `nohup` when running the import in background mode.
  - Confirm that the process is running:
    ```bash
    ps -p $(cat bootstrap-logs/bootstrap.pid)
    ```
- **Out of Memory Errors:**
  - Reduce the number of parallel jobs with `--jobs`.
  - Ensure sufficient RAM is available (128-256MB per job recommended).
