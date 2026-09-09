# Record Stream to Block Stream Cutover Guide

## Purpose

This guide walks mirror node operators (commercial providers and self-hosted deployments) through the steps needed to stay compatible as Hedera replaces the Record Stream with the Block Stream, per [HIP-1193: Record Stream to Block Stream Cutover](https://hips.hedera.com/hip/hip-1193). Each step links to the relevant design documentation and tooling for the details.

> **Note:** The dates and minimum version below reflect the current cutover plan and may be adjusted. Always treat the latest version of this guide as authoritative.

## Who Needs to Take Action

- **Mirror node operators** — both commercial providers and self-hosted deployments.
- **Direct consumers of record files** who read the legacy network streams bucket directly rather than through mirror node software.

**Not affected:** the Hedera API (HAPI), the Hedera SDKs, the Mirror Node Explorer, the Mirror Node REST API, and the JSON-RPC Relay. Application developers built on top of a mirror node do not need to change any code.

## Timeline

| Milestone                              | Date                  | Notes                                                                               |
| -------------------------------------- | --------------------- | ----------------------------------------------------------------------------------- |
| Testnet cutover (consensus node v0.80) | **November 25, 2026** | Block stream becomes canonical on testnet; validate your upgrade here first.        |
| Mainnet cutover (consensus node v0.80) | **December 9, 2026**  | Block stream (with network-aggregated TSS signatures) becomes canonical on mainnet. |
| Minimum `hiero-mirror-node` version    | **v0.164.0**          | Hard floor — releases earlier than this stop ingesting new data at cutover.         |

If your mirror node isn't upgraded by the relevant date, it will **stop ingesting new data** on that network. The legacy network streams bucket you read today (e.g. `hedera-mainnet-streams`) and its historical record files remain available after cutover — nothing is deleted, but new data stops being written there.

## What's Changing

Record streams, event streams, signature files, and sidecars are unified into a single signed Block Stream format ([HIP-1056](https://hips.hedera.com/hip/hip-1056)). Consensus nodes push this stream over gRPC to new Block Node infrastructure ([HIP-1081](https://hips.hedera.com/hip/hip-1081)); mirror nodes read from block nodes, with cloud storage remaining available as a fallback. See the design docs in the `hiero-mirror-node` repo for the full technical detail:

- [`docs/design/block-streams.md`](https://github.com/hiero-ledger/hiero-mirror-node/blob/main/docs/design/block-streams.md) — HIP-1056 block stream ingestion and the block-to-record transformation used during the transition.
- [`docs/design/block-node-support.md`](https://github.com/hiero-ledger/hiero-mirror-node/blob/main/docs/design/block-node-support.md) — HIP-1081 support for streaming blocks directly from block nodes, including the `AUTO` source-switching behavior.
- [`docs/design/block-node-discoverabilty.md`](https://github.com/hiero-ledger/hiero-mirror-node/blob/main/docs/design/block-node-discoverabilty.md) — HIP-1137 on-chain block node discovery, which lets the importer automatically learn about block node endpoints rather than requiring them to be hand-configured.

## What You Need to Do

### 1. Upgrade `hiero-mirror-node`

Upgrade to **v0.164.0 at minimum**, validated against testnet before November 25 and confirmed on mainnet before December 9. Treat v0.164.0 as a floor, not a target — keep moving to each newer release shipped between now and the mainnet cutover for the smoothest transition, then return to your normal upgrade cadence once mainnet cutover is complete.

### 2. Pre-grant read IAM access to the new buckets

Grant read access to the following **requester-pays** buckets for the same principal that currently reads your existing network streams bucket:

| Network    | Bucket                                   |
| ---------- | ---------------------------------------- |
| mainnet    | `hedera-mainnet-recent-block-streams`    |
| testnet    | `hedera-testnet-recent-block-streams`    |
| previewnet | `hedera-previewnet-recent-block-streams` |

Use the [`tools/blockstream/verify-block-streams-bucket-access.sh`](https://github.com/hiero-ledger/hiero-mirror-node/blob/main/tools/blockstream/verify-block-streams-bucket-access.sh) script to confirm your credentials can `LIST` and `GET` objects in these buckets ahead of time — see [`docs/runbook/verify-recent-block-streams-bucket-access.md`](https://github.com/hiero-ledger/hiero-mirror-node/blob/main/docs/runbook/verify-recent-block-streams-bucket-access.md) for full usage instructions. Example:

```bash
tools/blockstream/verify-block-streams-bucket-access.sh -p aws -n testnet \
    --access-key AKIA... --secret-key ...
```

A successful run prints `All checks passed.` and exits `0`.

### 3. Review (but likely don't need to change) block stream configuration

You do **not** need to change any configuration to get _through_ the cutover — the importer auto-detects it and switches sources automatically. There is, however, one recommended step _after_ cutover completes: see the note below the table. The relevant properties, documented in full in [`docs/configuration.md`](https://github.com/hiero-ledger/hiero-mirror-node/blob/main/docs/configuration.md), are:

| Property                                           | Default | Description                                                                                                                                                                                                                                                                                     |
| -------------------------------------------------- | ------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `hiero.mirror.importer.block.enabled`              | `false` | Whether the block stream source is enabled. The importer streams blocks through the cutover even while this is `false`; once cutover completes it logs a warning asking you to set this `true` and restart (see note below). Block stream and record downloader cannot both be enabled at once. |
| `hiero.mirror.importer.block.sourceType`           | `AUTO`  | `AUTO`, `BLOCK_NODE`, or `FILE`. In `AUTO`, `BLOCK_NODE` is tried first, then falls back to `FILE`.                                                                                                                                                                                             |
| `hiero.mirror.importer.block.cutover.enabled`      |         | Overrides whether the record-to-block-stream cutover is enabled for your instance. Defaults per-network (on for mainnet/testnet); leave unset to use the network default.                                                                                                                       |
| `hiero.mirror.importer.block.cutover.hapiVersion`  | 0.79.0  | The HAPI version after which the final cutover happens. Advanced/testing use only — leave unset in production unless you have a specific reason to override the network default.                                                                                                                |
| `hiero.mirror.importer.block.cutover.threshold`    | 16s     | Time to wait when switching between block stream and record stream during cutover.                                                                                                                                                                                                              |
| `hiero.mirror.importer.block.autoDiscoveryEnabled` | `true`  | Whether the importer auto-discovers block nodes registered on-chain (HIP-1137). Set to `false` to rely solely on manually configured `block.nodes[]`.                                                                                                                                           |
| `hiero.mirror.importer.block.nodes[]`              |         | Explicit block node endpoints. Not required if relying on on-chain block node discovery (HIP-1137).                                                                                                                                                                                             |

Leave `sourceType` at its `AUTO` default unless you have a specific reason to pin to `BLOCK_NODE` or `FILE`. Only configure `block.nodes[]` manually if you are not relying on automatic block node discovery, or want to pin to a specific block node with a given `priority`.

> **After cutover completes**, the importer detects that the last stream file it processed is a block stream and logs a warning like: _"Cutover has completed for network `<network>`, please set `hiero.mirror.importer.block.enabled=true` and restart."_ It flips the flag in memory so ingestion keeps working, but you should **persist `hiero.mirror.importer.block.enabled=true`** (and let the record downloader be disabled — the two are mutually exclusive) so the setting survives restarts and the warning stops. This is the one config change worth making, and only _after_ the network has cut over.

### 4. Confirm ingestion after cutover

After the cutover date for a given network, verify:

```
GET /api/v1/blocks?order=desc&limit=1
```

returns a block timestamp within roughly 5 seconds of wall clock time. This confirms your mirror node is keeping pace with the network.

To confirm specifically that the **block stream** (rather than the record stream) is being produced and consumed, check the file extension of the latest stream files: block stream files use a `.blk` extension (or `.blk.zstd` when compressed), whereas record files use `.rcd`/`.rcd.gz`. Seeing `.blk`/`.blk.zstd` objects in the `hedera-{network}-recent-block-streams` bucket is a simple, reliable signal that the network has cut over to the block stream.

## Troubleshooting / FAQ

**What happens if I don't upgrade in time?**

Your mirror node stops ingesting new data at the cutover for that network. It does not crash or error loudly — the importer simply has nothing compatible to read, so check `/api/v1/blocks` freshness rather than assuming silence means success.

**What if my block node connection fails after cutover?**

In `AUTO` mode, once a block has been streamed from a block node and validated, the importer prefers `BLOCK_NODE` going forward, but `CompositeBlockSource` will fall back to `FILE` (cloud storage) if the block node source becomes unhealthy (currently, after a few — 3 — consecutive block download failures; the exact threshold may change between releases). See [`docs/design/block-node-support.md`](https://github.com/hiero-ledger/hiero-mirror-node/blob/main/docs/design/block-node-support.md#compositeblocksource) for the full switching logic.

**Do I need to manually configure block node endpoints?**

Not necessarily. [HIP-1137](https://github.com/hiero-ledger/hiero-mirror-node/blob/main/docs/design/block-node-discoverabilty.md) introduces on-chain block node discovery, which the importer uses to automatically learn endpoints via `hiero.mirror.importer.block.autoDiscoveryEnabled` (default `true`). Manual `block.nodes[]` configuration is only needed if you want to override or supplement auto-discovered nodes.

**Is my historical data affected?**

No. The legacy network streams bucket and its record files remain available after cutover; only new data stops being written there.

## Resources

- [HIP-1056: Block Streams](https://hips.hedera.com/hip/hip-1056)
- [HIP-1081: Block Node](https://hips.hedera.com/hip/hip-1081)
- [HIP-1137: Block Node Discoverability](https://hips.hedera.com/hip/hip-1137)
- [HIP-1193: Record Stream to Block Stream Cutover](https://hips.hedera.com/hip/hip-1193)
- [`hiero-mirror-node` releases](https://github.com/hiero-ledger/hiero-mirror-node/releases)
- [Hedera announcement blog post](https://hedera.com/blog/block-streams-replace-the-record-stream-by-default-starting-september-2026-action-required-by-mirror-node-operators/) (background only)
- Mirror node operator discussion: [Hedera Discord](https://hedera.com/discord)
