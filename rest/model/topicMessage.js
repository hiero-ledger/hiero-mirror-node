// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';

class TopicMessage {
  /**
   * Parses topic_message table columns into object
   */
  constructor(topicMessage) {
    Object.assign(
      this,
      _.mapKeys(topicMessage, (v, k) => _.camelCase(k))
    );
  }

  static tableAlias = 'tm';
  static tableName = 'topic_message';

  static CHUNK_NUM = 'chunk_num';
  static CHUNK_TOTAL = 'chunk_total';
  static CONSENSUS_TIMESTAMP = 'consensus_timestamp';
  static INITIAL_TRANSACTION_ID = 'initial_transaction_id';
  static MESSAGE = 'message';
  static PAYER_ACCOUNT_ID = 'payer_account_id';
  static RUNNING_HASH = 'running_hash';
  static RUNNING_HASH_VERSION = 'running_hash_version';
  static SEQUENCE_NUMBER = 'sequence_number';
  static TOPIC_ID = 'topic_id';
  static VALID_START_TIMESTAMP = 'valid_start_timestamp';

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

export default TopicMessage;
