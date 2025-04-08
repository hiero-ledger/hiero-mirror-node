// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';
import {proto} from '@hashgraph/proto';
import {FileDecodeError} from '../errors/index.js';

class FeeSchedule {
  static FEE_DIVISOR_FACTOR = 1000n;

  /**
   * Parses fee schedule into object
   */
  constructor(feeSchedule) {
    let currentAndNextFeeSchedule = {};

    try {
      currentAndNextFeeSchedule = proto.CurrentAndNextFeeSchedule.decode(feeSchedule.file_data);
    } catch (error) {
      throw new FileDecodeError(error.message);
    }

    this.current_feeSchedule = _.get(currentAndNextFeeSchedule, 'currentFeeSchedule.transactionFeeSchedule') || [];
    this.next_feeSchedule = _.get(currentAndNextFeeSchedule, 'nextFeeSchedule.transactionFeeSchedule') || [];
    this.timestamp = feeSchedule.consensus_timestamp;
  }
}

export default FeeSchedule;
