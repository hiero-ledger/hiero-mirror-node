# Bootstrap Tech-Specs

High-performance Go binary for bootstrapping Hedera Mirror Node PostgreSQL databases from exported data files.

## Overview

This tool imports large CSV data exports into PostgreSQL using parallel workers, streaming decompression, and single-pass hash validation.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                          CLI (cobra)                                 │
│  init │ import │ status │ watch                                     │
└───────────────────────────┬─────────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────────┐
│                       Worker Pool                                    │
│  • Bounded channel (jobs * 2 buffer)                                │
│  • N parallel workers (configurable via -j flag)                    │
│  • Results channel for completion tracking                          │
└───────────────────────────┬─────────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────────┐
│                    Per-Worker Pipeline                               │
│  ┌─────────┐   ┌─────────┐   ┌─────────┐   ┌─────────┐             │
│  │  File   │──▶│ TeeRead │──▶│  pgzip  │──▶│  COPY   │             │
│  │  Open   │   │ +BLAKE3 │   │ decomp  │   │ stream  │             │
│  └─────────┘   └─────────┘   └─────────┘   └─────────┘             │
│                     │                            │                   │
│                     ▼                            ▼                   │
│              Hash validated            Rows imported to PG           │
└─────────────────────────────────────────────────────────────────────┘
```

**Key design decisions:**

- **Single-pass processing**: TeeReader computes BLAKE3 hash while streaming to decompressor
- **No temp files**: Compressed data streams directly to PostgreSQL COPY
- **Bounded parallelism**: Worker pool prevents overwhelming the database
- **Crash recovery**: JSON-based tracking file survives restarts

## Package Structure

```
cmd/mirrornode-bootstrap/     # CLI entry point
internal/
├── buffers/                  # sync.Pool for reusable byte slices
├── config/                   # Environment/config file parsing
├── database/                 # PostgreSQL connection setup
├── importer/                 # COPY streaming, partition detection
├── manifest/                 # CSV manifest parsing (filename→hash→size)
├── progress/                 # Real-time progress via pg_stat_progress_copy
├── tracking/                 # Resume state (JSON file)
├── util/                     # File utilities
└── worker/                   # Bounded worker pool implementation
```

## Commands

### `init` - Initialize Database

Creates the database, users, and applies the schema from `schema.sql.gz`.

```bash
./bootstrap init -c bootstrap.env -d /path/to/data
```

### `import` - Import Data Files

Imports CSV files from manifest with parallel workers.

```bash
./bootstrap import \
  -c bootstrap.env \
  -d /path/to/data \
  -m /path/to/manifest.csv \
  -j 10                        # 10 parallel workers
```

Flags:

- `-c, --config`: Path to bootstrap.env configuration file
- `-d, --data-dir`: Directory containing data files
- `-m, --manifest`: Path to manifest.csv file
- `-j, --jobs`: Number of parallel workers (default: 8)

### `status` - Check Import Status

Shows current import progress from tracking file.

```bash
./bootstrap status -c bootstrap.env
```

### `watch` - Monitor Progress

Displays real-time import progress in a separate terminal while import is running.

```bash
./bootstrap watch \
  -c bootstrap.env \
  -m /path/to/manifest.csv \
  -d /path/to/data
```

Flags:

- `-c, --config`: Path to bootstrap.env configuration file
- `-m, --manifest`: Path to manifest.csv file (enables row count and percentage)
- `-d, --data-dir`: Directory containing data files
- `-i, --interval`: Refresh interval in seconds (default: 1)

## Configuration

Uses shell-style config file (`bootstrap.env`):

```bash
export PGHOST="127.0.0.1"
export PGPORT="5432"
export PGUSER="postgres"
export PGPASSWORD="admin_password"
export PGDATABASE="postgres"
export OWNER_PASSWORD="mirror_node_password"
export REST_PASSWORD="mirror_rest_password"
export GRPC_PASSWORD="mirror_grpc_password"
export ROSETTA_PASSWORD="mirror_rosetta_password"
export WEB3_PASSWORD="mirror_web3_password"
export GRAPH_READER_PASSWORD="graphql_read_password"
```

Environment variables override config file values.

## Performance Characteristics

| Operation          | Performance                                                    |
| ------------------ | -------------------------------------------------------------- |
| BLAKE3 hashing     | 2.9 GB/s (SIMD)                                                |
| gzip decompression | 400-600 MB/s (parallel, 4 threads)                             |
| PostgreSQL COPY    | 200-400K rows/s per worker (depends on tables' data structure) |
| Buffer allocation  | Near-zero (pooled)                                             |

## Concurrency Model

1. **Job submission**: Runs in a dedicated goroutine to avoid deadlock
2. **Worker pool**: Fixed number of workers consuming from bounded channel
3. **Results collection**: Main goroutine drains results channel
4. **Progress monitor**: Dedicated connection polls `pg_stat_progress_copy`

The worker pool uses channels with buffer size `workers * 2` to balance throughput and memory. Job submission runs asynchronously to prevent deadlock when both job and result channels fill.

## Resume Behavior

State is tracked in `bootstrap-logs/tracking.json`:

```json
{
  "file.csv.gz": { "status": "IMPORTED", "hash_status": "HASH_VERIFIED" },
  "file2.csv.gz": { "status": "IN_PROGRESS", "hash_status": "HASH_UNVERIFIED" }
}
```

On restart:

- `IMPORTED` files are skipped
- `IN_PROGRESS`, `FAILED_TO_IMPORT`, and `FAILED_VALIDATION` files are reset to `NOT_STARTED` before import begins
- All non-`IMPORTED` files are then queued for import in manifest order
- Each file's import handles its own data cleanup before loading:
  - For partitioned tables, the partition is truncated before re-import
  - For non-partitioned tables (where the partition name from the filename doesn't exist in the DB),
    the file's time range is derived from the `_pYYYY_MM` filename suffix and a targeted
    `DELETE WHERE consensus_timestamp >= start AND < end` clears only that month's data before re-import

## Output Files

All logs and state are written to `bootstrap-logs/`:

| File                          | Purpose                                     |
| ----------------------------- | ------------------------------------------- |
| `bootstrap.log`               | Full structured logs                        |
| `tracking.json`               | JSON file for resume state (human-readable) |
| `progress.txt`                | Last progress snapshot                      |
| `bootstrap_discrepancies.log` | Files where imported rows ≠ expected        |
| `bootstrap.pid`               | PID file for single-instance                |

## Building

First, ensure dependencies are up to date:

```bash
go mod tidy
```

### Build for Linux x86_64

```bash
GOOS=linux GOARCH=amd64 go build -o bootstrap ./cmd/mirrornode-bootstrap/
```

### Build for macOS x86_64 (Intel)

```bash
GOOS=darwin GOARCH=amd64 go build -o bootstrap ./cmd/mirrornode-bootstrap/
```

### Build for macOS ARM64 (Apple Silicon)

```bash
GOOS=darwin GOARCH=arm64 go build -o bootstrap ./cmd/mirrornode-bootstrap/
```

### Build for current platform

```bash
go build -o bootstrap ./cmd/mirrornode-bootstrap/
```

## Testing

```bash
go test ./...
```

## Debug Mode

Enable verbose logging:

```bash
DEBUG_MODE=true ./bootstrap import ...
```

This logs connection pool stats, worker job assignments, and acquire timing.
