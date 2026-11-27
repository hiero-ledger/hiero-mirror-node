// SPDX-License-Identifier: Apache-2.0

import {fromBinary} from '@bufbuild/protobuf';
import {CustomFeeLimitSchema} from '../gen/services/custom_fees_pb.js';
import {RestError} from '../errors';

class CustomFeeLimits {
  static MAX_ENTRIES = 500;

  /**
   * Parses an array of serialized CustomFeeLimit messages from transaction.max_custom_fees. At most MAX_ENTRIES
   * entries are parsed; any beyond that are dropped.
   *
   * @param {Buffer[]} customFeeLimits - An array of byte arrays representing serialized CustomFeeLimit messages.
   * @throws {RestError} if any entry is not a valid serialized CustomFeeLimit
   */
  constructor(customFeeLimits) {
    if (!customFeeLimits || customFeeLimits.length === 0) {
      this.fees = [];
      return;
    }

    if (customFeeLimits.length > CustomFeeLimits.MAX_ENTRIES) {
      logger.warn(
        `Truncating max_custom_fees from ${customFeeLimits.length} to ${CustomFeeLimits.MAX_ENTRIES} entries`
      );
    }

    this.fees = customFeeLimits
      .slice(0, CustomFeeLimits.MAX_ENTRIES)
      .map((feeBytes, index) => CustomFeeLimits.parse(feeBytes, index));
  }

  static parse(feeBytes, index) {
    try {
      return fromBinary(CustomFeeLimitSchema, feeBytes);
    } catch (error) {
      throw new RestError(`Failed to parse max_custom_fees entry at index ${index}`, {cause: error});
    }
  }
}

export default CustomFeeLimits;
