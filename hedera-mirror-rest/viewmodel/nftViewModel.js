// SPDX-License-Identifier: Apache-2.0

import EntityId from '../entityId';
import {encodeBase64, nsToSecNs} from '../utils';

/**
 * NFT view model
 */
class NftViewModel {
  constructor(nftModel) {
    this.account_id = EntityId.parse(nftModel.accountId, {isNullable: true}).toString();
    this.created_timestamp = nsToSecNs(nftModel.createdTimestamp);
    this.delegating_spender = EntityId.parse(nftModel.delegatingSpender, {isNullable: true}).toString();
    this.deleted = nftModel.deleted;
    this.metadata = encodeBase64(nftModel.metadata);
    this.modified_timestamp = nsToSecNs(nftModel.timestampRange?.begin);
    this.serial_number = nftModel.serialNumber;
    this.spender = EntityId.parse(nftModel.spender, {isNullable: true}).toString();
    this.token_id = EntityId.parse(nftModel.tokenId).toString();
  }
}

export default NftViewModel;
