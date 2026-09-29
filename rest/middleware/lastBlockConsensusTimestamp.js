// SPDX-License-Identifier: Apache-2.0

import RecordFileService from '../service/recordFileService';

const CACHE_MILLIS = 500;

export const LAST_BLOCK_CONSENSUS_TIMESTAMP_HEADER = 'x-last-block-consensus-timestamp';

let cachedAt = 0;
let cachedValue;
let pending;

const loadLastBlockConsensusTimestamp = async () => {
  const now = Date.now();
  if (now - cachedAt < CACHE_MILLIS) {
    return cachedValue;
  }

  if (!pending) {
    pending = RecordFileService.getLatestConsensusEnd()
      .then((value) => {
        cachedAt = Date.now();
        cachedValue = value;
        return value;
      })
      .finally(() => {
        pending = undefined;
      });
  }

  return pending;
};

const setLastBlockConsensusTimestampHeader = async (res) => {
  try {
    const timestamp = await loadLastBlockConsensusTimestamp();
    if (timestamp != null) {
      res.set(LAST_BLOCK_CONSENSUS_TIMESTAMP_HEADER, String(timestamp));
    }
  } catch (error) {
    logger.warn(`Failed to read last block consensus timestamp: ${error.message}`);
  }
};

export default setLastBlockConsensusTimestampHeader;
