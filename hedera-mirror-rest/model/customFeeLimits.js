// SPDX-License-Identifier: Apache-2.0

import {proto} from '@hashgraph/proto';
import FixedFee from './fixedFee.js';

class CustomFeeLimits {
  /**
   * Parses an array of serialized CustomFeeLimit messages from transaction.max_custom_fees
   * @param {Buffer[]} customFeeLimits - An array of byte arrays representing serialized CustomFeeLimit messages.
   */
  constructor(customFeeLimits) {
    if (!Array.isArray(customFeeLimits) || customFeeLimits.length === 0) {
      this.fees = [];
      return;
    }
    this.fees = (customFeeLimits ?? []).map((feeBytes) => {
      const customFeeLimitSet = proto.CustomFeeLimit.decode(feeBytes);
      return {
        accountId: customFeeLimitSet.accountId,
        fixedFees: (customFeeLimitSet.fees ?? []).map((fee) => new FixedFee(fee)),
      };
    });
  }
}

export default CustomFeeLimits;
