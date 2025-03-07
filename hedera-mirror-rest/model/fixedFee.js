// SPDX-License-Identifier: Apache-2.0

import Fee from './fee';

class FixedFee extends Fee {
  /**
   * Parses fixed_fee from element in custom_fee.fixed_fees jsonb column
   */
  constructor(fixedFee) {
    super(fixedFee);
    this.amount = fixedFee.amount;
    this.denominatingTokenId = fixedFee.denominating_token_id;
  }

  static AMOUNT = `amount`;
  static DENOMINATING_TOKEN_ID = `denominating_token_id`;
}

export default FixedFee;
