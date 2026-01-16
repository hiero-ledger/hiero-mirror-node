// SPDX-License-Identifier: Apache-2.0

import {proto} from '@hashgraph/proto';

import EntityId from '../entityId';
import {nsToSecNs} from '../utils';

/**
 * TransactionId view model
 */
class TransactionIdViewModel {
  /**
   * Constructs transactionId view model from proto transaction id or TransactionId model
   *
   * @param {TransactionId|TransactionID} transactionId
   */
  constructor(transactionId) {
    if (transactionId instanceof proto.TransactionID) {
      // handle proto format
      const {accountID, transactionValidStart, nonce, scheduled} = transactionId;
      this.account_id = EntityId.of(accountID.shardNum, accountID.realmNum, accountID.accountNum).toString();
      this.nonce = nonce;
      this.scheduled = scheduled;
      this.transaction_valid_start = `${transactionValidStart.seconds}.${transactionValidStart.nanos
        .toString()
        .padStart(9, '0')}`;
    } else {
      // handle db format. Handle nil case for nonce and scheduled
      this.account_id = EntityId.parse(transactionId.payerAccountId).toString();
      this.nonce = transactionId.nonce;
      this.scheduled = transactionId.scheduled;
      this.transaction_valid_start = nsToSecNs(transactionId.validStartTimestamp);
    }
  }
}

export default TransactionIdViewModel;
