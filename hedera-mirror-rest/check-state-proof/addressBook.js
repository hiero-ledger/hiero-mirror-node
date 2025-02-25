// SPDX-License-Identifier: Apache-2.0

import log4js from 'log4js';
import {proto} from '@hashgraph/proto';

const logger = log4js.getLogger();

class AddressBook {
  /**
   * Parses address book file storing map of node account id -> rsa public key
   */
  constructor(addressBook) {
    this.parseAddressBookBuffer(addressBook);
    this.setNodeAccountIdPublicKeyPairs();
  }

  parseAddressBookBuffer(addressBookBuffer) {
    const addressBook = proto.NodeAddressBook.decode(addressBookBuffer);
    logger.info(`${addressBook.nodeAddress.length} node(s) found in address book`);
    this.nodeList = addressBook.nodeAddress;
  }

  setNodeAccountIdPublicKeyPairs() {
    this.nodeAccountIdPublicKeyPairs = Object.fromEntries(
      this.nodeList.map((nodeAddress) => {
        const {memo, nodeAccountId, RSA_PubKey} = nodeAddress;
        // For some address books nodeAccountId does not exist, in those cases retrieve id from memo field
        const nodeAccountIdStr = nodeAccountId
          ? [nodeAccountId.shardNum, nodeAccountId.realmNum, nodeAccountId.accountNum].join('.')
          : memo.toString('utf8');
        return [nodeAccountIdStr, RSA_PubKey];
      })
    );
  }
}

export default AddressBook;
