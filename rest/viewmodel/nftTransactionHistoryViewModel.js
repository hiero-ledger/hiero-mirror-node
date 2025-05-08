// SPDX-License-Identifier: Apache-2.0

import EntityId from '../entityId';
import {createTransactionId, nsToSecNs} from '../utils';
import {TransactionType} from '../model';

const defaultNftTransfer = {
  isApproval: false,
  receiverAccountId: null,
  senderAccountId: null,
};

/**
 * Nft transaction history transfer view model
 */
class NftTransactionHistoryViewModel {
  constructor(transactionModel) {
    this.consensus_timestamp = nsToSecNs(transactionModel.consensusTimestamp);
    this.nonce = transactionModel.nonce;
    this.transaction_id = createTransactionId(
      EntityId.parse(transactionModel.payerAccountId).toString(),
      transactionModel.validStartNs
    );
    this.type = TransactionType.getName(transactionModel.type);

    // transactionModel is for a specific NFT in a single transaction, the nftTransfer array should contain at most
    // one such nft transfer. However, per issue #3815, services
    // in the past externalizes multiple transfers for an NFT in the record if it's transferred between multiple
    // parties, e.g., there could be two NFT transfers if it's first from Alice to Bob, then from Bob to Carol,
    // instead of a single flattened transfer from Alice to Carol.
    const nftTransfer = transactionModel.nftTransfer[0] ?? defaultNftTransfer;
    this.is_approval = nftTransfer.isApproval;
    this.receiver_account_id = EntityId.parse(nftTransfer.receiverAccountId, {isNullable: true}).toString();
    this.sender_account_id = EntityId.parse(nftTransfer.senderAccountId, {isNullable: true}).toString();
  }
}

export default NftTransactionHistoryViewModel;
