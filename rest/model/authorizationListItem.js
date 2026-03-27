// SPDX-License-Identifier: Apache-2.0

class AuthorizationListItem {
  /**
   * Parses authorization list item from element in ethereum_transaction.authorization_list jsonb column
   */
  constructor(authorizationListItem) {
    this.address = authorizationListItem.address;
    this.chain_id = authorizationListItem.chain_id;
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
