# Database Bootstrap Guide

This guide provides step-by-step instructions for setting up a fresh PostgreSQL database and importing Mirror Node data into it using the `bootstrap.sh` script and `bootstrap.env` configuration file. The process involves initializing the database, configuring environment variables, and running the import script. The data import is a long-running process, so it's important to ensure it continues running even if your SSH session is terminated.

---

## Table of Contents

- [Prerequisites](#prerequisites)
  - [1. Optional High-Performance Decompressors](#1-optional-high-performance-decompressors)
  - [2. Environment Variables for Thread Counts](#2-environment-variables-for-thread-counts)
- [Database Initialization and Data Import](#database-initialization-and-data-import)
  - [1. Download the Required Scripts and Configuration File](#1-download-the-required-scripts-and-configuration-file)
  - [2. Edit the `bootstrap.env` Configuration File](#2-edit-the-bootstrapenv-configuration-file)
  - [3. Download the Database Export Data](#3-download-the-database-export-data)
    - [3.1. Set Your Default GCP Project](#31-set-your-default-gcp-project)
    - [3.2. List Available Versions](#32-list-available-versions)
    - [3.3. Select a Version](#33-select-a-version)
    - [3.4. Download the Data](#34-download-the-data)
      - [Download Minimal DB Data Files](#download-minimal-db-data-files)
      - [Download Full DB Data Files](#download-full-db-data-files)
  - [4. Check Version Compatibility](#4-check-version-compatibility)
  - [5. Run the Bootstrap Script](#5-run-the-bootstrap-script)
  - [6. Monitoring and Managing the Import Process](#6-monitoring-and-managing-the-import-process)
    - [6.1. Monitoring the Import Process](#61-monitoring-the-import-process)
    - [6.2. Stopping the Script](#62-stopping-the-script)
    - [6.3. Resuming the Import Process](#63-resuming-the-import-process)
    - [6.4. Start the Mirrornode Importer](#64-start-the-mirrornode-importer)
- [Handling Failed Imports](#handling-failed-imports)
- [Additional Notes](#additional-notes)
- [Troubleshooting](#troubleshooting)

---

## Prerequisites

1. **PostgreSQL 16** installed and running.

2. Access to a machine where you can run the initialization and import scripts and connect to the PostgreSQL database.

3. Ensure the following tools are installed on your machine:

   - `awk`
   - `b3sum`
   - `basename`
   - `bc`
   - `cat`
   - `chmod`
   - `column`
   - `curl`
   - `date`
   - `dd`
   - `find`
   - `flock`
   - `grep`
   - `gunzip`
   - `head`
   - `mkdir`
   - `mkfifo`
   - `mktemp`
   - `mv`
   - `nproc`
   - `pgrep`
   - `pkill`
   - `ps`
   - `psql`
   - `python3`
   - `realpath`
   - `rm`
   - `sed`
   - `sleep`
   - `sort`
   - `stat`
   - `tail`
   - `touch`
   - `tr`
   - `wc`

   ### 1. Optional High-Performance Decompressors

   The script automatically detects and uses faster alternatives to `gunzip` if they are available in the system's or user's PATH:

   - [rapidgzip](https://github.com/mxmlnkn/rapidgzip) - A high-performance parallel gzip decompressor (fastest option, even for single-threaded decompression)
   - [igzip](https://github.com/intel/isa-l) - Intel's optimized gzip implementation from ISA-L (second fastest option)

   These tools can significantly improve decompression performance during the import process. If neither is available, the script will fall back to using standard `gunzip`.

   ### 2. Environment Variables for Thread Counts

   Thread counts for certain parallel operations can be configured via environment variables before running the script. If not set, defaults will be used.

   **Important:** These thread counts apply _per parallel import job_ launched by the main script, not globally for the entire script. For example, if `MAX_JOBS` allows 4 concurrent import jobs and `B3SUM_THREADS` is set to 2, up to 8 threads could be used for hashing simultaneously across those jobs.

   - `export B3SUM_THREADS=N` (Default: 2) - Number of threads for `b3sum` hash calculation.
   - `export DECOMPRESSOR_THREADS=N` (Default: 2) - Number of threads used _only if_ `rapidgzip` or `igzip` is installed and selected by the script for decompression.

   **Note:** The default `gunzip` decompressor is single-threaded and is not affected by these environment variables. When setting these values, be mindful of the CPU resources on the machine _running the script_. Setting thread counts too high relative to available cores can lead to CPU overcontention and potentially slow down the overall process rather than speeding it up.

4. Install the [Google Cloud SDK](https://cloud.google.com/sdk/docs/install), then authenticate:

   ```bash
   gcloud auth login
   ```

5. A Google Cloud Platform (GCP) account with a valid billing account attached (required for downloading data from a Requester Pays bucket). For detailed instructions on obtaining the necessary GCP information, refer to [Hedera's documentation](https://docs.hedera.com/hedera/core-concepts/mirror-nodes/run-your-own-beta-mirror-node/run-your-own-mirror-node-gcs#id-1.-obtain-google-cloud-platform-requester-pay-information).

---

## Database Initialization and Data Import

### 1. Download the Required Scripts and Configuration File

Download the `bootstrap.sh` script and the `bootstrap.env` configuration file. The `bootstrap.env` file comes with default values and needs to be edited to set your specific configurations.

**Steps:**

1. **Download `bootstrap.sh` and `bootstrap.env`:**

   ```bash
   curl -O https://raw.githubusercontent.com/hiero-ledger/hiero-mirror-node/main/hedera-mirror-importer/src/main/resources/db/scripts/bootstrap.sh \
        -O https://raw.githubusercontent.com/hiero-ledger/hiero-mirror-node/main/hedera-mirror-importer/src/main/resources/db/scripts/bootstrap.env

   chmod +x bootstrap.sh
   ```

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

- **(Optional) Configure Parallelism and Progress Monitoring:**

  ```bash
  # Progress tracking refresh interval in seconds
  export PROGRESS_INTERVAL=10

  # Parallelism for compression and checksum calculation
  export DECOMPRESSOR_THREADS=2 # Number of threads for selected decompressor (rapidgzip or igzip)
  export B3SUM_THREADS=2        # Number of threads for b3sum
  ```

  - `PROGRESS_INTERVAL`: How often (in seconds) the progress monitor updates `bootstrap_progress.log` (Default: 10).
  - `DECOMPRESSOR_THREADS`: Number of threads used _only if_ `rapidgzip` or `igzip` is installed and selected for decompression (Default: 2).
  - `B3SUM_THREADS`: Number of threads for `b3sum` hash calculation (Default: 2).
  - **Note:** The thread counts apply _per parallel import job_. Be mindful of the CPU resources on the machine _running the script_ to avoid overcontention.

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

> [!WARNING]
> Bootstrap of a full database export is currently **not supported** by the `bootstrap.sh` script in this version. Support for full database bootstrap will be added in the near future. The instructions below are for informational purposes only. For full-db support, you may use the previous version by checking out the following branch: `git checkout release/0.127`. The previous version is compatible only with the data files of the `0.113.2` export in the GCS bucket.

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

### 5. Run the Bootstrap Script

The `bootstrap.sh` script initializes the database and imports the data. It is designed to be a one-stop solution for setting up your Mirror Node database.

**Instructions:**

1. **Ensure You Have `bootstrap.sh` and `bootstrap.env` in the Same Directory:**

   ```bash
   ls -l bootstrap.*
   # Should list bootstrap.sh and bootstrap.env
   ```

2. **Run the Bootstrap Script Using `setsid` and Redirect Output to `bootstrap.log`:**

   To ensure the script continues running even if your SSH session is terminated, run it in a new session using `setsid`. The script handles its own logging, but we redirect stderr to capture any startup errors:

   For a minimal database import (default):

   ```bash
   setsid ./bootstrap.sh 8 /path/to/db_export 2>> bootstrap.log
   ```

   For a full database import:

   > [!WARNING]
   > The `--full` option for a full database import is currently **not supported** in this version of the `bootstrap.sh` script. Using this flag will result in errors and a failed database bootstrap.

   ```bash
   setsid ./bootstrap.sh 8 --full /path/to/db_export 2>> bootstrap.log
   ```

   - The script handles logging internally to `bootstrap.log`, and the execution command will also append stderr to the log file
   - `8` refers to the number of CPU cores to use for parallel processing. Adjust this number based on your system's resources.
   - `/path/to/db_export` is the directory where you downloaded the database export data.
   - The script creates several tracking and logging files:

     - `bootstrap.log`: Main log file for all script operations. Timestamps now use abbreviated month names (e.g., 'Apr' instead of '04').
     - `bootstrap.pid`: Stores the process ID of the main script, used for managing the process group (e.g., for termination). The script performs an improved check at startup to avoid conflicts with unrelated processes that might share a stale PID.
     - `bootstrap_tracking.txt`: Tracks the progress of each file's import and hash verification.
     - `bootstrap_progress.log`: Displays real-time progress of active `psql COPY` operations (see Monitoring section).
     - `bootstrap_discrepancies.log`: Records any data verification issues (e.g., row count mismatches). Only created if discrepancies are found.

   - **Important**: The `SKIP_DB_INIT` flag file is automatically created by the script after a successful database initialization. Do not manually create or delete this file. If you need to force the script to reinitialize the database in future runs, remove the flag file using:

     ```bash
     rm -f SKIP_DB_INIT
     ```

3. **Verify the Script is Running:**

   ```bash
   tail -f bootstrap.log
   watch --color -n .1 "cat bootstrap_progress.log | tail -n $(($(tput lines) - 2))"
   ```

   - Monitor the progress in the log and progress tracking files and check for any errors or warnings.

4. **Disconnect Your SSH Session (Optional):**

   You can safely close your SSH session. The script will continue running in the background.

### 6. Monitoring and Managing the Import Process

#### **6.1. Monitoring the Import Process:**

- **Check the Main Log File:**

  ```bash
  tail -f bootstrap.log
  ```

  - The script logs all activity to `bootstrap.log`.
  - Note that the script processes files in parallel and asynchronously. Activities are logged as they occur, so log entries may appear in an arbitrary order.

- **Check the Progress Log File:**

  - To continuously view the latest progress with automatic screen refreshing, use the following `watch` command:

  ```bash
  watch --color -n .1 "cat bootstrap_progress.log | tail -n $(($(tput lines) - 2))"
  ```

  - This command uses `watch` to re-run the `cat | tail` command every 0.1 seconds. `$(($(tput lines) - 2))` calculates the number of lines in your current terminal and subtracts 2, and `tail -n` uses this value to display the most recent log lines that fit your screen height.

  - This file provides a real-time, formatted view of active import jobs. It typically updates every `$PROGRESS_INTERVAL` seconds (default: 10 seconds, configurable in `bootstrap.env`).
  - Columns include:
    - `Filename`: The data file being imported.
    - `Rows_Processed`: Current count of rows processed by `psql COPY`.
    - `Total_Rows`: Expected total rows for the file from the manifest.
    - `Percentage`: Calculated completion percentage.
    - `Rate(rows/s)`: Estimated import rate in rows per second based on recent progress.
  - If the monitor cannot query the database or encounters issues, an error message will be displayed in this file. Check `bootstrap.log` for more details in that case.

- **Check the Tracking File:**

  ```bash
  cat bootstrap_tracking.txt
  ```

  - This file tracks the status of each file being imported.
  - Each line contains the file name, followed by two status indicators:
    - Import Status:
      - `NOT_STARTED`: File has not begun processing.
      - `IN_PROGRESS`: File is currently being validated or imported.
      - `IMPORTED`: File was successfully validated and imported.
      - `FAILED_VALIDATION`: File failed size or hash check.
      - `FAILED_TO_IMPORT`: File import process failed (e.g., `psql` error).
    - Hash Verification Status:
      - `HASH_UNVERIFIED`: BLAKE3 hash has not been verified yet.
      - `HASH_VERIFIED`: BLAKE3 hash verification passed.
      - `UNVERIFIED_COUNT`: (Fallback) Import completed, but row count query failed (e.g., couldn't extract partition info).
      - `ROW_COUNT_UNVERIFIED`: (Fallback) Import completed, primary row count query failed, but a basic data existence check passed.

#### **6.2. Stopping the Script**

If you need to stop the script before it completes:

1. **Gracefully Terminate the Script and All Child Processes:**

   ```bash
   kill -TERM -- -$(cat bootstrap.pid)
   ```

   - Sends the `SIGTERM` signal to the entire process group identified by the PID in `bootstrap.pid`.
   - Allows the script and all its background processes to perform cleanup (including removing temporary DB objects and files) and exit gracefully.

2. **If the Script Doesn't Stop, Force Termination of the Process Group:**

   ```bash
   kill -KILL -$(cat bootstrap.pid)
   ```

   - Sends the `SIGKILL` signal to the entire process group.
   - Immediately terminates the script and its children. **Cleanup might not complete successfully.** This is generally discouraged unless graceful termination does not work.

#### **6.3. Resuming the Import Process**

- **Re-run the Bootstrap Script:**

  > [!WARNING]
  > The `--full` option for resuming a full database import is currently **not supported** in this version.

  ```bash
  # Minimal DB
  setsid ./bootstrap.sh 8 /path/to/db_export 2>> bootstrap.log

  # Full DB
  setsid ./bootstrap.sh 8 --full /path/to/db_export 2>> bootstrap.log
  ```

  - The script will resume where it left off, skipping files that have already been imported successfully.
  - Add the `--full` flag if you were performing a full database import previously.

#### **6.4. Start the Mirrornode Importer**

- Once the bootstrap process completes without errors (check `bootstrap.log` for a success message and verify `bootstrap_tracking.txt` shows all files as `IMPORTED HASH_VERIFIED`), you may start the Mirrornode Importer.

---

## Handling Failed Imports

During the import process, the script generates `bootstrap_tracking.txt`, which logs the status of each file import (see Monitoring section for status descriptions). If any files end with `FAILED_VALIDATION` or `FAILED_TO_IMPORT`, the script will exit with an error status.

**Example of `bootstrap_tracking.txt`:**

```
schema.sql.gz IMPORTED HASH_VERIFIED
MIRRORNODE_VERSION.gz IMPORTED HASH_VERIFIED
account_balance_p2024_01.csv.gz IMPORTED HASH_VERIFIED
transaction_p2019_08.csv.gz FAILED_VALIDATION HASH_UNVERIFIED
transaction_p2019_09.csv.gz FAILED_TO_IMPORT HASH_VERIFIED
token_balance_p2024_01.csv.gz IMPORTED HASH_VERIFIED
```

**Notes on Data Consistency:**

- **Automatic Retry:** When you re-run the `bootstrap.sh` script, it will automatically attempt to import files marked NOT marked as "IMPORTED".
- **Data Integrity:** The script uses single transactions for `psql COPY` operations, ensuring that no partial data is committed in case of an import failure for a specific file. Hash and size validation prevent corrupted data files from being imported.
- **Concurrent Write Safety:** The script uses file locking (`flock`) to safely handle concurrent writes to tracking and log files.

---

## Additional Notes

- **System Resources:**
  - Adjust the number of CPU cores used (`8` in the example) based on your system's capabilities.
  - Monitor system resources (CPU, memory, I/O) on both the client machine and the database server during the import process to identify potential bottlenecks.
- **Security Considerations:**
  - Secure your `bootstrap.env` file and any other files containing sensitive information.
- **Environment Variables:**
  - Ensure `bootstrap.env` is in the same directory as `bootstrap.sh`.

---

## Troubleshooting

- **Connection Errors:**
  - Confirm that `PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD`, `PGDATABASE` in `bootstrap.env` are correctly set for the _initial connection_ (usually as `postgres` user to the `postgres` database). The script switches credentials internally after initialization.
  - Ensure that the database server allows connections from your client machine (check `pg_hba.conf` and firewall rules).
  - Verify that the database port (`PGPORT`) is correct and accessible.
- **Import Failures:**
  - Review `bootstrap.log` for detailed error messages from `psql`, `b3sum`, decompressors, or the script itself.
  - Check `bootstrap_tracking.txt` to identify which files failed validation or import.
  - Check `bootstrap_discrepancies.log` for specific row count mismatches (this file is only created if discrepancies are found).
  - Check `bootstrap_progress.log` for errors related to the progress monitor itself.
  - Re-run the `bootstrap.sh` script to retry importing failed files.
- **Permission Denied Errors:**
  - Ensure that the initial user specified in `PGUSER` has superuser privileges or sufficient permissions to create databases and roles.
  - Verify that file system permissions allow the script to read the data directory and write log/tracking files in the current directory.
- **Environment Variable Issues:**
  - Double-check that all required variables in `bootstrap.env` are correctly set and exported.
  - Ensure there are no typos or missing variables. Check `bootstrap.log` for errors related to sourcing the environment file.
- **Script Does Not Continue After SSH Disconnect:**
  - Ensure you used `setsid` when running the script.
  - Confirm that the script is running by checking the process list:
    ```bash
    ps -p $(cat bootstrap.pid)
    ```
