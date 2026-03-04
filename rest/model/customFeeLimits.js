// SPDX-License-Identifier: Apache-2.0

import {proto} from '@hiero-ledger/proto';

class CustomFeeLimits {
  /**
   * Parses an array of serialized CustomFeeLimit messages from transaction.max_custom_fees
   * @param {Buffer[]} customFeeLimits - An array of byte arrays representing serialized CustomFeeLimit messages.
   */
  constructor(customFeeLimits) {
    if (!customFeeLimits || customFeeLimits.length === 0) {
      this.fees = [];
      return;
    }

    this.fees = customFeeLimits.map((feeBytes) => proto.CustomFeeLimit.decode(feeBytes));
  }
}

export default CustomFeeLimits;
