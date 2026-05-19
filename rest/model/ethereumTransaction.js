// SPDX-License-Identifier: Apache-2.0

import isEmpty from 'lodash/isEmpty';
import camelCase from 'lodash/camelCase';
import mapKeys from 'lodash/mapKeys';

import config from '../config';

class EthereumTransaction {
  /**
   * Parses ethereum_transaction table columns into object
   */
  constructor(ethereumTransaction) {
    Object.assign(
      this,
      mapKeys(ethereumTransaction, (v, k) => camelCase(k))
    );

    this.accessList = EthereumTransaction.normalizeAccessList(this.accessList);

    if (config.response.enableDelegationAddress) {
      if (this.authorizationList == null) {
        this.authorizationList = [];
      }
    } else {
      delete this.authorizationList;
    }
  }

  static tableAlias = 'etht';
  static tableName = 'ethereum_transaction';

  static ACCESS_LIST = 'access_list';
  static AUTHORIZATION_LIST = 'authorization_list';
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

  /**
   * Normalizes access_list from JSONB array. Legacy bytea values are returned as an empty array since
   * Buffer.prototype.map returns a Buffer, not an array of access list items.
   *
   * @param {Array|Buffer|string|null} accessList
   * @returns {Array|null}
   */
  static normalizeAccessList(accessList) {
    if (accessList == null) {
      return null;
    }

    if (Buffer.isBuffer(accessList)) {
      return [];
    }

    if (typeof accessList === 'string') {
      if (isEmpty(accessList)) {
        return [];
      }

      try {
        const parsed = JSON.parse(accessList);
        return Array.isArray(parsed) ? parsed : [];
      } catch {
        return [];
      }
    }

    return Array.isArray(accessList) ? accessList : [];
  }
}

export default EthereumTransaction;
