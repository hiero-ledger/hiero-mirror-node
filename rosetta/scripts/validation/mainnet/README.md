# Validation Configuration

The [configuration file](./validation.json) is tailored to run rosetta-cli `check:data` for mainnet with large amount
of data in a performant way.

- `start_index` is set to 1 to work around the known rosetta-cli performance issue of loading large genesis account
  balance JSON file
- memory limit is disabled to improve both block syncing and account reconciliation speed
- pruning is disabled to avoid the badger db overhead during such operation
- query timeout and retry is tuned to avoid rosetta-cli to abort early when the rosetta server is under heavy load thus
  can't answer queries in time intermittently
- the `check:data` progress is saved in db in the `./data` directory, so it can be resumed when interrupted

It's recommended to run rosetta-cli with at least 16 vCPUs and 100GB memory.

Occasionally rosetta-cli will consume too much memory and get killed by the system OOM killer. When that happens, before
resuming, remove `"start_index": 1` from the configuration file so rosetta-cli will not wipe the saved state and restart
from index 1.
