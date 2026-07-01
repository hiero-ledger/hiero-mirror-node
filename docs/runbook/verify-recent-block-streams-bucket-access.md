# Verify Recent Block Streams Bucket Access

## Problem

To ensure a smooth record stream to block stream cutover (defined in [HIP-1193: Record Stream to Block Stream Cutover](https://hips.hedera.com/hip/hip-1193)), a third-party operator needs to confirm that their cloud credentials can`LIST`
and `GET` objects in the Hedera "recent block streams" buckets, ahead of time. These buckets are
**requester-pays**, so the caller must supply credentials and a billing account/project of their own. The
[`tools/blockstream/verify-block-streams-bucket-access.sh`](../../tools/blockstream/verify-block-streams-bucket-access.sh)
script performs that check against AWS S3 or GCS for a chosen network.

The buckets checked (identical names across both providers) are:

| Network    | Bucket                                   |
| ---------- | ---------------------------------------- |
| mainnet    | `hedera-mainnet-recent-block-streams`    |
| testnet    | `hedera-testnet-recent-block-streams`    |
| previewnet | `hedera-previewnet-recent-block-streams` |

## What it does

For the selected provider and network the script runs two requester-pays checks and prints a PASS/FAIL/SKIP summary:

1. **LIST** — lists a single object from the bucket to confirm list permission.
2. **GET** — reads the first object returned by the list to confirm read permission.

The script exits `0` when all checks pass and `1` when any check fails.

## Requirements

- **AWS**: `aws` CLI v2.
- **GCS**: `awscurl` and `xmllint` (`libxml2-utils`). GCS is accessed through its S3-compatible XML API, so a GCS
  interoperability HMAC key is used as the access/secret key, and a billing project is required via `--project`.

## Usage

All commands are assumed to be run from the git repository root.

```bash
tools/blockstream/verify-block-streams-bucket-access.sh [options]
```

Options:

| Option                       | Description                                           |
| ---------------------------- | ----------------------------------------------------- |
| `-p, --provider <aws\|gcs>`  | Which cloud to check (required)                       |
| `-n, --network <name>`       | `mainnet` \| `testnet` \| `previewnet` (required)     |
| `--access-key <key>`         | Access key id / GCS HMAC key (required)               |
| `--secret-key <secret>`      | Secret key / GCS HMAC secret (required)               |
| `--region <region>`          | AWS region for the bucket (if needed)                 |
| `--project <gcp-project-id>` | GCS billing project (required for `gcs`)              |
| `--no-range`                 | Do a full `GET` instead of a `bytes=0-0` ranged `GET` |
| `-v, --verbose`              | Print the operation/target for each check             |
| `-h, --help`                 | Show help                                             |

### Examples

```bash
# AWS, testnet
tools/blockstream/verify-block-streams-bucket-access.sh -p aws -n testnet \
    --access-key AKIA... --secret-key ...

# GCS, previewnet
tools/blockstream/verify-block-streams-bucket-access.sh -p gcs -n previewnet --project my-gcp-project \
    --access-key GOOG... --secret-key ...
```

## Verification

- A successful run prints `All checks passed.` and exits `0`.
- On failure, each failing check reports the underlying AWS/GCS error (e.g. an access-denied or requester-pays error),
  the summary line shows a non-zero `FAIL` count, and the script exits `1`.
