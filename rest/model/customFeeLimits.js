// SPDX-License-Identifier: Apache-2.0

import {fromBinary} from '@bufbuild/protobuf';
import {CustomFeeLimitSchema} from '../gen/services/custom_fees_pb.js';

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

    this.fees = customFeeLimits.map((feeBytes) => fromBinary(CustomFeeLimitSchema, feeBytes));
  }
}

export default CustomFeeLimits;
