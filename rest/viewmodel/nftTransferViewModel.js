// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';
import EntityId from '../entityId';

/**
 * Nft transfer view model
 */
class NftTransferViewModel {
  constructor(nftTransferModel) {
    this.is_approval = _.isNil(nftTransferModel.isApproval) ? false : nftTransferModel.isApproval;
    this.receiver_account_id = EntityId.parse(nftTransferModel.receiverAccountId, {isNullable: true}).toString();
    this.sender_account_id = EntityId.parse(nftTransferModel.senderAccountId, {isNullable: true}).toString();
    this.serial_number = nftTransferModel.serialNumber;
    this.token_id = EntityId.parse(nftTransferModel.tokenId).toString();
  }
}

export default NftTransferViewModel;
