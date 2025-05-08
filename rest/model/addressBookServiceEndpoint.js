// SPDX-License-Identifier: Apache-2.0

class AddressBookServiceEndpoint {
  /**
   * Parses crypto_allowance table columns into object
   */
  constructor(serviceEndpoint) {
    // explicitly assign properties to restict properties and allow for composition in other models
    this.consensusTimestamp = serviceEndpoint.consensus_timestamp;
    this.domainName = serviceEndpoint.domain_name;
    this.ipAddressV4 = serviceEndpoint.ip_address_v4;
    this.nodeId = serviceEndpoint.node_id;
    this.port = serviceEndpoint.port;
  }

  static tableAlias = 'abse';
  static tableName = 'address_book_service_endpoint';

  static CONSENSUS_TIMESTAMP = 'consensus_timestamp';
  static DOMAIN_NAME = 'domain_name';
  static IP_ADDRESS_V4 = 'ip_address_v4';
  static NODE_ID = 'node_id';
  static PORT = 'port';

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

export default AddressBookServiceEndpoint;
