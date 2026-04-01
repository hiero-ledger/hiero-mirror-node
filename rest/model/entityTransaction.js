// SPDX-License-Identifier: Apache-2.0

import camelCase from 'lodash/camelCase';
import mapKeys from 'lodash/mapKeys';

class EntityTransaction {
  /**
   * Parses entity_transaction table columns into object
   */
  constructor(entityTransaction) {
    Object.assign(
      this,
      mapKeys(entityTransaction, (v, k) => camelCase(k))
    );
  }

  static tableAlias = 'et';
  static tableName = 'entity_transaction';

  static CONSENSUS_TIMESTAMP = 'consensus_timestamp';
  static ENTITY_ID = 'entity_id';
  static PAYER_ACCOUNT_ID = 'payer_account_id';
  static RESULT = 'result';
  static TYPE = 'type';

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

export default EntityTransaction;
