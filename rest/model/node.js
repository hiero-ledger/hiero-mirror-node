// SPDX-License-Identifier: Apache-2.0

import ServiceEndpoint from './serviceEndpoint';

class Node {
  static tableAlias = 'n';
  static tableName = 'node';
  static ADMIN_KEY = `admin_key`;
  static ACCOUNT_ID = `account_id`;
  static CREATED_TIMESTAMP = `created_timestamp`;
  static DECLINE_REWARD = `decline_reward`;
  static DELETED = `deleted`;
  static GRPC_PROXY_ENDPOINT = `grpc_proxy_endpoint`;
  static NODE_ID = `node_id`;
  static TIMESTAMP_RANGE = `timestamp_range`;

  /**
   * Parses node table columns into object
   */
  constructor(node) {
    this.adminKey = node.admin_key;
    this.createdTimestamp = node.created_timestamp;
    this.declineReward = node.decline_reward;
    this.deleted = node.deleted;
    this.grpcProxyEndpoint = node.grpc_proxy_endpoint != null ? new ServiceEndpoint(node.grpc_proxy_endpoint) : null;
    this.nodeId = node.node_id;
    this.timestampRange = node.timestamp_range;
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

export default Node;
