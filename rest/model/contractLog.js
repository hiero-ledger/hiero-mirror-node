// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';

class ContractLog {
  /**
   * Parses contract_log table columns into object
   */
  constructor(contractLog, recordFile) {
    Object.assign(
      this,
      _.mapKeys(contractLog, (v, k) => _.camelCase(k))
    );
    this.blockHash = recordFile?.hash;
    this.blockNumber = recordFile?.index ?? null;
  }

  static tableAlias = 'cl';
  static tableName = 'contract_log';

  static BLOOM = 'bloom';
  static CONSENSUS_TIMESTAMP = 'consensus_timestamp';
  static CONTRACT_ID = 'contract_id';
  static DATA = 'data';
  static INDEX = 'index';
  static PAYER_ACCOUNT_ID = 'payer_account_id';
  static ROOT_CONTRACT_ID = 'root_contract_id';
  static TOPIC0 = 'topic0';
  static TOPIC1 = 'topic1';
  static TOPIC2 = 'topic2';
  static TOPIC3 = 'topic3';
  static TRANSACTION_HASH = 'transaction_hash';
  static TRANSACTION_INDEX = 'transaction_index';

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

export default ContractLog;
