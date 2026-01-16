// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';

class ContractResult {
  /**
   * Parses contract_result table columns into object
   */
  constructor(contractResult) {
    Object.assign(
      this,
      _.mapKeys(contractResult, (v, k) => _.camelCase(k))
    );
  }

  static tableAlias = 'cr';
  static tableName = 'contract_result';

  static AMOUNT = 'amount';
  static BLOOM = 'bloom';
  static CALL_RESULT = 'call_result';
  static CONSENSUS_TIMESTAMP = 'consensus_timestamp';
  static CONTRACT_ID = 'contract_id';
  static CREATED_CONTRACT_IDS = 'created_contract_ids';
  static ERROR_MESSAGE = 'error_message';
  static FAILED_INITCODE = 'failed_initcode';
  static FUNCTION_PARAMETERS = 'function_parameters';
  static FUNCTION_RESULT = 'function_result';
  static GAS_CONSUMED = 'gas_consumed';
  static GAS_LIMIT = 'gas_limit';
  static GAS_USED = 'gas_used';
  static PAYER_ACCOUNT_ID = 'payer_account_id';
  static SENDER_ID = 'sender_id';
  static TRANSACTION_HASH = 'transaction_hash';
  static TRANSACTION_INDEX = 'transaction_index';
  static TRANSACTION_NONCE = 'transaction_nonce';
  static TRANSACTION_RESULT = 'transaction_result';

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

export default ContractResult;
