// SPDX-License-Identifier: Apache-2.0

import Fee from './fee';

class FractionalFee extends Fee {
  /**
   * Parses fractional_fee from element in custom_fee.fractional_fees jsonb column
   */
  constructor(fractionalFee) {
    super(fractionalFee);
    this.denominator = fractionalFee.denominator;
    this.maximumAmount = fractionalFee.maximum_amount;
    this.minimumAmount = fractionalFee.minimum_amount;
    this.netOfTransfers = fractionalFee.net_of_transfers;
    this.numerator = fractionalFee.numerator;
  }

  static DENOMINATOR = `denominator`;
  static MAXIMUM_AMOUNT = `maximum_amount`;
  static MINIMUM_AMOUNT = `minimum_amount`;
  static NET_OF_TRANSFERS = `net_of_transfers`;
  static NUMERATOR = `numerator`;
}

export default FractionalFee;
