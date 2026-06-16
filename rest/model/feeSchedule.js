// SPDX-License-Identifier: Apache-2.0

import {fromBinary} from '@bufbuild/protobuf';
import {CurrentAndNextFeeScheduleSchema} from '../gen/services/basic_types_pb.js';
import {FileDecodeError} from '../errors';

class FeeSchedule {
  constructor(feeScheduleFile) {
    try {
      this.feeSchedule = fromBinary(CurrentAndNextFeeScheduleSchema, feeScheduleFile.file_data);
    } catch (error) {
      throw new FileDecodeError(error.message);
    }

    this.consensus_timestamp = feeScheduleFile.consensus_timestamp;
  }
}

export default FeeSchedule;
