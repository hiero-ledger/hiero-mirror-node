// SPDX-License-Identifier: Apache-2.0

import * as utils from '../utils.js';

class AuthorizationListItem {
  /**
   * Parses authorization list item from element in ethereum_transaction.authorization_list jsonb column
   */
  constructor(authorizationListItem) {
    this.address = utils.toHexString(utils.stripHexPrefix(authorizationListItem.address), true, 40);
    this.chain_id = utils.toHexStringQuantity(utils.stripHexPrefix(authorizationListItem.chain_id));
    this.nonce = authorizationListItem.nonce;
    this.r = authorizationListItem.r;
    this.s = authorizationListItem.s;
    this.y_parity = authorizationListItem.y_parity;
  }

  static ADDRESS = `address`;
  static CHAIN_ID = `chain_id`;
  static NONCE = `nonce`;
  static R = `r`;
  static S = `s`;
  static Y_PARITY = `y_parity`;
}

export default AuthorizationListItem;
