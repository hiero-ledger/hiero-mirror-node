/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
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

class CustomFeeLimitViewModel {
  /**
   * Formats the CustomFeeLimit data for API response.
   * @param {Object[]} fees - Array of parsed CustomFeeLimit objects.
   */
  constructor(fees) {
    this.max_custom_fees = fees.map((fee) => ({
      account_id: fee.accountId,
      amount: fee.fixedFees[0]?.amount || 0,
      denominating_token_id: fee.fixedFees[0]?.denominatingTokenId || null,
    }));
  }
}

export default CustomFeeLimitViewModel;
