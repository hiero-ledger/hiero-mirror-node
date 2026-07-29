// SPDX-License-Identifier: Apache-2.0

import {fromBinary} from '@bufbuild/protobuf';
import {Extra, FeeScheduleSchema} from '../gen/fees/fee_schedule_pb.js';
import {FileDecodeError} from '../errors';

class FeeSchedule {
  /**
   * @param {{file_data: Uint8Array|Buffer, consensus_timestamp: number|string|bigint}} feeScheduleFile
   */
  constructor(feeScheduleFile) {
    try {
      this.feeSchedule = fromBinary(FeeScheduleSchema, feeScheduleFile.file_data);
    } catch (error) {
      throw new FileDecodeError(error.message);
    }

    this.consensus_timestamp = feeScheduleFile.consensus_timestamp;
  }

  /**
   * @returns {bigint|null} GAS extra fee in tinycents, or null when missing
   */
  getGasPriceTinycents() {
    for (const extra of this.feeSchedule.extras ?? []) {
      if (extra.name === Extra.GAS) {
        return extra.fee;
      }
    }
    return null;
  }
}

export default FeeSchedule;
