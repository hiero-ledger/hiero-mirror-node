// SPDX-License-Identifier: Apache-2.0

import FixedFee from './fixedFee';
import FractionalFee from './fractionalFee';
import RoyaltyFee from './royaltyFee';

class CustomFee {
  static tableName = `custom_fee`;
  static tableAlias = `cf`;
  static ENTITY_ID = `entity_id`;
  static FIXED_FEES = `fixed_fees`;
  static FRACTIONAL_FEES = `fractional_fees`;
  static ROYALTY_FEES = `royalty_fees`;
  static TIMESTAMP_RANGE = `timestamp_range`;

  /**
   * Parses custom_fee table columns into object
   */
  constructor(customFee) {
    this.createdTimestamp = customFee.created_timestamp;
    this.fixedFees = (customFee.fixed_fees ?? []).map((n) => new FixedFee(n));
    this.fractionalFees = (customFee.fractional_fees ?? []).map((n) => new FractionalFee(n));
    this.royaltyFees = (customFee.royalty_fees ?? []).map((n) => new RoyaltyFee(n));
    this.tokenId = customFee.token_id;
  }

  /**
   * Gets full column name with table alias prepended.
   *
   * @param {string} columnName
   * @private
   */
  static getFullName(columnName) {
    return `${this.tableAlias}.${columnName}`;
  }
}

export default CustomFee;
