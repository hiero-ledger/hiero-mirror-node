// SPDX-License-Identifier: Apache-2.0

import {fromBinary} from '@bufbuild/protobuf';
import {Extra, FeeScheduleSchema} from '../gen/fees/fee_schedule_pb.js';
import {CurrentAndNextFeeScheduleSchema} from '../gen/services/basic_types_pb.js';
import {FileDecodeError} from '../errors';

class FeeSchedule {
  /**
   * @param {{file_data: Uint8Array|Buffer, consensus_timestamp: number|string|bigint}} feeScheduleFile
   */
  constructor(feeScheduleFile) {
    this.consensus_timestamp = feeScheduleFile.consensus_timestamp;

    // 1. Try parsing as PBJ simple FeeSchedule
    try {
      const parsedSimple = fromBinary(FeeScheduleSchema, feeScheduleFile.file_data);
      if (parsedSimple?.extras && parsedSimple.extras.length > 0) {
        this.simpleFeeSchedule = parsedSimple;
        return;
      }
    } catch {
      // Fall through to legacy format
    }

    // 2. Try parsing as legacy CurrentAndNextFeeSchedule
    try {
      this.feeSchedule = fromBinary(CurrentAndNextFeeScheduleSchema, feeScheduleFile.file_data);
    } catch (error) {
      throw new FileDecodeError(`Failed to parse fee schedule: ${error.message}`);
    }
  }

  /**
   * @returns {bigint|null} GAS extra fee in tinycents, or null when missing/not simple format
   */
  getGasPriceTinycents() {
    if (!this.simpleFeeSchedule?.extras) {
      return null;
    }

    for (const extra of this.simpleFeeSchedule.extras) {
      if (extra.name === Extra.GAS || extra.name === 'GAS') {
        return extra.fee;
      }
    }
    return null;
  }
}

export default FeeSchedule;
