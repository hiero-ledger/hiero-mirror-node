// SPDX-License-Identifier: Apache-2.0

import {addHexPrefix, nsToSecNs, toHexString} from '../utils';

/**
 * Block view model
 */
class BlockViewModel {
  /**
   * Constructs block view model
   *
   * @param {Block} recordFile
   */
  constructor(recordFile) {
    this.count = recordFile.count;
    this.hapi_version = recordFile.getFullHapiVersion();
    this.hash = addHexPrefix(recordFile.hash);
    this.name = recordFile.name;
    this.number = recordFile.index;
    this.previous_hash = addHexPrefix(recordFile.prevHash);
    this.size = recordFile.size;
    this.timestamp = {
      from: nsToSecNs(recordFile.consensusStart),
      to: nsToSecNs(recordFile.consensusEnd),
    };
    this.gas_used = recordFile.gasUsed === -1 ? null : recordFile.gasUsed;
    this.logs_bloom = recordFile.logsBloom ? toHexString(recordFile.logsBloom, true, 512) : null;
  }
}

export default BlockViewModel;
