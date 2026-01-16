// SPDX-License-Identifier: Apache-2.0

import Fee from './fee';
import FixedFee from './fixedFee';

class RoyaltyFee extends Fee {
  /**
   * Parses royalty_fee from element in custom_fee.royalty_fees jsonb column
   */
  constructor(royaltyFee) {
    super(royaltyFee);
    this.denominator = royaltyFee.denominator;
    this.fallbackFee = royaltyFee.fallback_fee ? new FixedFee(royaltyFee.fallback_fee) : null;
    this.numerator = royaltyFee.numerator;
  }

  static FALLBACK_FEE = `fallback_fee`;
  static DENOMINATOR = `denominator`;
  static NUMERATOR = `numerator`;
}

export default RoyaltyFee;
