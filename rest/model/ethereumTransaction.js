// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';

class EthereumTransaction {
  /**
   * Parses ethereum_transaction table columns into object
   */
  constructor(ethereumTransaction) {
    Object.assign(
      this,
      _.mapKeys(ethereumTransaction, (v, k) => _.camelCase(k))
    );
  }

  static tableAlias = 'etht';
  static tableName = 'ethereum_transaction';

  static ACCESS_LIST = 'access_list';
  static CALL_DATA_ID = 'call_data_id';
  static CALL_DATA = 'call_data';
  static CHAIN_ID = 'chain_id';
  static CONSENSUS_TIMESTAMP = 'consensus_timestamp';
  static DATA = 'data';
  static FROM_ADDRESS = 'from_address';
  static GAS_LIMIT = 'gas_limit';
  static GAS_PRICE = 'gas_price';
  static HASH = 'hash';
  static MAX_FEE_PER_GAS = 'max_fee_per_gas';
  static MAX_GAS_ALLOWANCE = 'max_gas_allowance';
  static MAX_PRIORITY_FEE_PER_GAS = 'max_priority_fee_per_gas';
  static NONCE = 'nonce';
  static PAYER_ACCOUNT_ID = 'payer_account_id';
  static RECOVERY_ID = 'recovery_id';
  static SIGNATURE_R = 'signature_r';
  static SIGNATURE_S = 'signature_s';
  static SIGNATURE_V = 'signature_v';
  static TO_ADDRESS = 'to_address';
  static TYPE = 'type';
  static VALUE = 'value';

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

export default EthereumTransaction;
