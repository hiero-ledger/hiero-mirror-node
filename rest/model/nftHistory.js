// SPDX-License-Identifier: Apache-2.0

import Nft from './nft';

class NftHistory extends Nft {
  /**
   * Parses nft history table columns into object
   */
  constructor(nftHistory) {
    super(nftHistory);
  }

  static tableAlias = 'nft_history';
  static tableName = this.tableAlias;

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

export default NftHistory;
