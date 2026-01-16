// SPDX-License-Identifier: Apache-2.0

import EntityId from '../entityId';
import {TokenFreezeStatus, TokenKycStatus} from '../model';
import {nsToSecNs} from '../utils';

/**
 * TokenRelationship view model
 */
class TokenRelationshipViewModel {
  /**
   * Constructs tokenRelationship view model
   *
   * @param {TokenRelationship} tokenRelationship
   */
  constructor(tokenRelationship) {
    this.automatic_association = tokenRelationship.automaticAssociation;
    this.balance = tokenRelationship.balance;
    this.created_timestamp = nsToSecNs(tokenRelationship.createdTimestamp);
    this.decimals = tokenRelationship.decimals;
    this.token_id = EntityId.parse(tokenRelationship.tokenId).toString();

    const {freezeStatus, kycStatus} = tokenRelationship;
    this.freeze_status = freezeStatus !== null ? new TokenFreezeStatus(freezeStatus) : null;
    this.kyc_status = kycStatus !== null ? new TokenKycStatus(kycStatus) : null;
  }
}

export default TokenRelationshipViewModel;
