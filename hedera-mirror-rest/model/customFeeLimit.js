/*
 * Copyright (C) 2021-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {proto} from '@hashgraph/proto';
import {FileDecodeError} from '../errors/index.js';
import FixedFee from './fixedFee.js';

class CustomFeeLimit {
  /**
   * Parses an array of serialized CustomFeeLimit messages from transaction.max_custom_fees
   * @param {Buffer[]} customFeeLimits - An array of byte arrays representing serialized CustomFeeLimit messages.
   */
  constructor(customFeeLimits) {
    this.fees = customFeeLimits.map((feeBytes) => {
      try {
        const customFeeLimitSet = proto.CustomFeeLimit.decode(feeBytes);
        return {
          accountId: customFeeLimitSet.accountId,
          fixedFees: (customFeeLimitSet.fees ?? []).map((fee) => new FixedFee(fee)),
        };
      } catch (error) {
        throw new FileDecodeError(error.message);
      }
    });
  }
}

export default CustomFeeLimit;
