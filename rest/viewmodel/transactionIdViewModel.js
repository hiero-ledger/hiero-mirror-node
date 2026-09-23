// SPDX-License-Identifier: Apache-2.0

import isNil from 'lodash/isNil';

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
    if (transactionId?.$typeName === 'proto.TransactionID') {
      const {accountID, transactionValidStart, nonce, scheduled} = transactionId;
      const acc = accountID?.account;
      this.account_id = isNil(acc?.value)
        ? null
        : EntityId.of(accountID.shardNum, accountID.realmNum, acc.value).toString();
      this.nonce = nonce;
      this.scheduled = scheduled;
      this.transaction_valid_start = isNil(transactionValidStart)
        ? null
        : `${transactionValidStart.seconds}.${String(transactionValidStart.nanos).padStart(9, '0')}`;
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
