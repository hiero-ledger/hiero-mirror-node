// SPDX-License-Identifier: Apache-2.0

class AddressBookEntry {
  /**
   * Parses address_book_entry table columns into object
   */
  constructor(addressBookEntry) {
    // explicitly assign properties to restrict properties and allow for composition in other models
    this.consensusTimestamp = addressBookEntry.consensus_timestamp;
    this.description = addressBookEntry.description;
    this.memo = addressBookEntry.memo;
    this.nodeAccountId = addressBookEntry.node_account_id;
    this.nodeCertHash = addressBookEntry.node_cert_hash;
    this.nodeId = addressBookEntry.node_id;
    this.publicKey = addressBookEntry.public_key;
    this.stake = addressBookEntry.stake;
  }

  static tableAlias = 'abe';
  static tableName = 'address_book_entry';

  static CONSENSUS_TIMESTAMP = 'consensus_timestamp';
  static MEMO = 'memo';
  static PUBLIC_KEY = 'public_key';
  static NODE_ID = 'node_id';
  static NODE_ACCOUNT_ID = 'node_account_id';
  static NODE_CERT_HASH = 'node_cert_hash';
  static DESCRIPTION = 'description';
  static STAKE = 'stake';

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

export default AddressBookEntry;
