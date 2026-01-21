// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';

class Entity {
  static historyTableName = 'entity_history';
  static tableAlias = 'e';
  static tableName = 'entity';
  static ALIAS = 'alias';
  static AUTO_RENEW_ACCOUNT_ID = 'auto_renew_account_id';
  static AUTO_RENEW_PERIOD = 'auto_renew_period';
  static CREATED_TIMESTAMP = 'created_timestamp';
  static DELEGATION_ADDRESS = 'delegation_address';
  static DELETED = 'deleted';
  static ETHEREUM_NONCE = 'ethereum_nonce';
  static EVM_ADDRESS = 'evm_address';
  static EXPIRATION_TIMESTAMP = 'expiration_timestamp';
  static ID = 'id';
  static KEY = 'key';
  static MAX_AUTOMATIC_TOKEN_ASSOCIATIONS = 'max_automatic_token_associations';
  static MEMO = 'memo';
  static NUM = 'num';
  static OBTAINER_ID = 'obtainer_id';
  static PERMANENT_REMOVAL = 'permanent_removal';
  static PROXY_ACCOUNT_ID = 'proxy_account_id';
  static PUBLIC_KEY = 'public_key';
  static REALM = 'realm';
  static RECEIVER_SIG_REQUIRED = 'receiver_sig_required';
  static SHARD = 'shard';
  static SUBMIT_KEY = 'submit_key';
  static TIMESTAMP_RANGE = 'timestamp_range';
  static TYPE = 'type';

  /**
   * Parses entity table columns into object
   */
  constructor(entity) {
    Object.assign(
      this,
      _.mapKeys(entity, (v, k) => _.camelCase(k))
    );
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

export default Entity;
