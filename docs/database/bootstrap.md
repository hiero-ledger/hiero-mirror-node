# Database Bootstrap Guide

This guide provides step-by-step instructions for setting up a fresh PostgreSQL database and importing Mirror Node data into it using the `bootstrap.sh` script and `bootstrap.env` configuration file. The process involves initializing the database, configuring environment variables, and running the import script. The data import is a long-running process, so it's important to ensure it continues running even if your SSH session is terminated.

---

## Table of Contents

- [Prerequisites](#prerequisites)
  - [1. Optional High-Performance Decompressors](#1-optional-high-performance-decompressors)
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

   - `psql`
   - `gunzip`
   - `realpath`
   - `flock`
   - `curl`
   - `b3sum`

   ### 1. Optional High-Performance Decompressors

   The script automatically detects and uses faster alternatives to `gunzip` if they are available in the system's or user's PATH:

   - [rapidgzip](https://github.com/mxmlnkn/rapidgzip) - A high-performance parallel gzip decompressor (fastest option, even for single-threaded decompression)
   - [igzip](https://github.com/intel/isa-l) - Intel's optimized gzip implementation from ISA-L (second fastest option)

   These tools can significantly improve decompression performance during the import process. If neither is available, the script will fall back to using standard `gunzip`.

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
mkdir -p /path/to/db_export
export CLOUDSDK_STORAGE_SLICED_OBJECT_DOWNLOAD_MAX_COMPONENTS=1 && \
VERSION_NUMBER=<VERSION_NUMBER> && \
gcloud storage rsync -r -x '.*_part_\d+_\d+_\d+_atma\.csv\.gz$' "gs://mirrornode-db-export/$VERSION_NUMBER/" /path/to/db_export/
```

##### Download Full DB Data Files

Create a directory and download all files and subdirectories for the selected version:

```bash
mkdir -p /path/to/db_export
export CLOUDSDK_STORAGE_SLICED_OBJECT_DOWNLOAD_MAX_COMPONENTS=1 && \
VERSION_NUMBER=<VERSION_NUMBER> && \
gcloud storage rsync -r "gs://mirrornode-db-export/$VERSION_NUMBER/" /path/to/db_export/
```

For both options:

- Replace `/path/to/db_export` with your desired directory path.
- Replace `<VERSION_NUMBER>` with the version you selected (e.g., `0.111.0`).
- Ensure all files and subdirectories are downloaded into this single parent directory.
- **Note:** The `-m` flag enables parallel downloads to speed up the process.

### 4. Check Version Compatibility

After downloading the data, it's crucial to ensure version compatibility between the database export and the Mirror Node you're setting up.

**Steps:**

1. **Locate the `MIRRORNODE_VERSION` File:**

   - The downloaded data should include a file named `MIRRORNODE_VERSION` in the root of the `/path/to/db_export` directory.

2. **Check the Mirror Node Version:**

   ```bash
   cat /path/to/db_export/MIRRORNODE_VERSION
   ```

3. **Ensure Version Compatibility:**

   - The version number in the `MIRRORNODE_VERSION` file should match the name of the directory from which you downloaded the data, and should also be the version of the Mirror Node you are initializing with this export's data.

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
   setsid ./bootstrap.sh 8 /path/to/db_export > /dev/null 2>> bootstrap.log &
   ```

   For a full database import:

   ```bash
   setsid ./bootstrap.sh 8 --full /path/to/db_export > /dev/null 2>> bootstrap.log &
   ```

   - The script handles logging internally to `bootstrap.log`, and the execution command will also append stderr to the log file
   - `8` refers to the number of CPU cores to use for parallel processing. Adjust this number based on your system's resources.
   - `/path/to/db_export` is the directory where you downloaded the database export data.
   - The script creates several tracking files:

     - `bootstrap.pid` stores the process ID used for cleanup of all child processes if interrupted
     - `bootstrap_tracking.txt` tracks the progress of each file's import and hash verification
     - `bootstrap_discrepancies.log` records any data verification issues

   - **Important**: The SKIP_DB_INIT flag file is automatically created by the script after a successful database initialization. Do not manually create or delete this file. If you need to force the script to reinitialize the database in future runs, remove the flag file using:

     ```bash
       rm -f SKIP_DB_INIT
     ```

3. **Verify the Script is Running:**

   ```bash
   tail -f bootstrap.log
   ```

   - Monitor the progress and check for any errors.

4. **Disconnect Your SSH Session (Optional):**

   You can safely close your SSH session. The script will continue running in the background.

### 6. Monitoring and Managing the Import Process

#### **6.1. Monitoring the Import Process:**

- **Check the Log File:**

  ```bash
  tail -f bootstrap.log
  ```

  - The script logs all activity to `bootstrap.log`.
  - Note that the script processes files in parallel and asynchronously. Activities are logged as they occur, so log entries may appear in an arbitrary order.

- **Check the Tracking File:**

  ```bash
  cat bootstrap_tracking.txt
  ```

  - This file tracks the status of each file being imported.
  - Each line contains the file name, followed by two status indicators:

    Import Status:

    - `NOT_STARTED`: File has not begun importing
    - `IN_PROGRESS`: File is currently being imported
    - `IMPORTED`: File was successfully imported
    - `FAILED_TO_IMPORT`: File import failed

    Hash Verification Status:

    - `HASH_UNVERIFIED`: BLAKE3 hash has not been verified yet
    - `HASH_VERIFIED`: BLAKE3 hash verification passed
    - `HASH_FAILED`: BLAKE3 hash verification failed

#### **6.2. Stopping the Script**

If you need to stop the script before it completes:

1. **Gracefully Terminate the Script and All Child Processes:**

   ```bash
   kill -TERM -- -$(cat bootstrap.pid)
   ```

   - Sends the `SIGTERM` signal to the entire process group.
   - Allows the script and all its background processes to perform cleanup and exit gracefully.

2. **If the Script Doesn't Stop, Force Termination of the Process Group:**

   ```bash
   kill -KILL -$(cat bootstrap.pid)
   ```

   - Sends the `SIGKILL` signal to the entire process group.
   - Immediately terminates the script, however may leave some background jobs running; It is recommended to use the first method.

**Note:** Ensure that `bootstrap.sh` is designed to handle termination signals and clean up its child processes appropriately.

#### **6.3. Resuming the Import Process**

- **Re-run the Bootstrap Script:**

  ```bash
  setsid ./bootstrap.sh 8 /path/to/db_export > /dev/null 2>> bootstrap.log &
  ```

  - The script will resume where it left off, skipping files that have already been imported successfully.
  - Add the `--full` flag if you were using full database mode.

#### **6.4. Start the Mirrornode Importer**

- Once the bootstrap process completes without errors, you may start the Mirrornode Importer.

---

## Handling Failed Imports

During the import process, the script generates a file named `bootstrap_tracking.txt`, which logs the status of each file import. Each line in this file contains the path and name of a file, followed by its import and hash verification status (see [Monitoring and Managing the Import Process](#6-monitoring-and-managing-the-import-process) for status descriptions).

**Example of `bootstrap_tracking.txt`:**

```
/path/to/db_export/record_file.csv.gz IMPORTED HASH_VERIFIED
/path/to/db_export/transaction/transaction_part_1.csv.gz IMPORTED HASH_VERIFIED
/path/to/db_export/transaction/transaction_part_2.csv.gz FAILED_TO_IMPORT HASH_FAILED
/path/to/db_export/account.csv.gz NOT_STARTED HASH_UNVERIFIED
```

**Notes on Data Consistency:**

- **Automatic Retry:** When you re-run the `bootstrap.sh` script, it will automatically attempt to import files marked as `NOT_STARTED`, `IN_PROGRESS`, or `FAILED_TO_IMPORT`.

- **Data Integrity:** The script ensures that no partial data is committed in case of an import failure.

- **Concurrent Write Safety:** The script uses file locking (`flock`) to safely handle concurrent writes to `bootstrap_tracking.txt`.

---

## Additional Notes

- **System Resources:**

  - Adjust the number of CPU cores used (`8` in the example) based on your system's capabilities.
  - Monitor system resources during the import process to ensure optimal performance.

- **Security Considerations:**

  - Secure your `bootstrap.env` file and any other files containing sensitive information.

- **Environment Variables:**

  - Ensure `bootstrap.env` is in the same directory as `bootstrap.sh`.

---

## Troubleshooting

- **Connection Errors:**

  - Confirm that `PGHOST` in `bootstrap.env` is correctly set.
  - Ensure that the database server allows connections from your client machine.
  - Verify that the database port (`PGPORT`) is correct and accessible.

- **Import Failures:**

  - Review `bootstrap.log` for detailed error messages.
  - Check `bootstrap_tracking.txt` to identify which files failed to import.
  - Check `bootstrap_discrepancies.log` for any data verification issues (this file is only created if discrepancies are found in file size, row count, or BLAKE3 hash verification).
  - Re-run the `bootstrap.sh` script to retry importing failed files.

- **Permission Denied Errors:**

  - Ensure that the user specified in `PGUSER` has the necessary permissions to create databases and roles.
  - Verify that file permissions allow the script to read and write to the necessary directories and files.

- **Environment Variable Issues:**

  - Double-check that all required variables in `bootstrap.env` are correctly set and exported.
  - Ensure there are no typos or missing variables.

- **Script Does Not Continue After SSH Disconnect:**

  - Ensure you used `setsid` when running the script.
  - Confirm that the script is running by checking the process list:

    ```bash
    ps -p $(cat bootstrap.pid)
    ```
