// SPDX-License-Identifier: Apache-2.0

import camelCase from 'lodash/camelCase';
import mapKeys from 'lodash/mapKeys';

class TopicMessageLookup {
  /**
   * Parses topic_message table columns into object
   */
  constructor(topicMessageLookup) {
    Object.assign(
      this,
      mapKeys(topicMessageLookup, (v, k) => camelCase(k))
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
