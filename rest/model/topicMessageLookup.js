// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';

class TopicMessageLookup {
  /**
   * Parses topic_message table columns into object
   */
  constructor(topicMessageLookup) {
    Object.assign(
      this,
      _.mapKeys(topicMessageLookup, (v, k) => _.camelCase(k))
    );
  }

  static tableAlias = 'tml';
  static tableName = 'topic_message_lookup';

  static PARTITION = 'partition';
  static SEQUENCE_NUMBER_RANGE = 'sequence_number_range';
  static TIMESTAMP_RANGE = 'timestamp_range';
  static TOPIC_ID = 'topic_id';

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

export default TopicMessageLookup;
