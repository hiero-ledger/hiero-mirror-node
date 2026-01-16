// SPDX-License-Identifier: Apache-2.0

import AddressBook from './addressBook';
import AddressBookEntry from './addressBookEntry';
import AddressBookServiceEndpoint from './addressBookServiceEndpoint';
import Node from './node';
import NodeStake from './nodeStake';

class NetworkNode {
  /**
   * Parses address book related table columns into object
   */
  constructor(networkNodeDb) {
    this.addressBook = new AddressBook(networkNodeDb);
    this.addressBookEntry = new AddressBookEntry(networkNodeDb);
    this.addressBookServiceEndpoints = networkNodeDb.service_endpoints.map((x) => new AddressBookServiceEndpoint(x));
    this.nodeStake = new NodeStake(networkNodeDb);
    this.node = new Node(networkNodeDb);
  }
}

export default NetworkNode;
