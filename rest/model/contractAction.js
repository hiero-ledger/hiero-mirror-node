// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';

class ContractAction {
  static tableAlias = 'cact';
  static tableName = 'contract_action';

  static CALL_DEPTH = 'call_depth';
  static CALL_OPERATION_TYPE = 'call_operation_type';
  static CALL_TYPE = 'call_type';
  static CALLER = 'caller';
  static CALLER_TYPE = 'caller_type';
  static CONSENSUS_TIMESTAMP = 'consensus_timestamp';
  static GAS = 'gas';
  static GAS_USED = 'gas_used';
  static INDEX = 'index';
  static INPUT = 'input';
  static PAYER_ACCOUNT_ID = 'payer_account_id';
  static RECIPIENT_ACCOUNT = 'recipient_account';
  static RECIPIENT_ADDRESS = 'recipient_address';
  static RECIPIENT_CONTRACT = 'recipient_contract';
  static RESULT_DATA = 'result_data';
  static RESULT_DATA_TYPE = 'result_data_type';
  static VALUE = 'value';

  /**
   * Parses contract_action table columns into object
   */
  constructor(contractAction) {
    Object.assign(
      this,
      _.mapKeys(contractAction, (v, k) => _.camelCase(k))
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

export default ContractAction;
