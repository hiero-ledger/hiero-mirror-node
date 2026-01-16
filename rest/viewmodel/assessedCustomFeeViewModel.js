// SPDX-License-Identifier: Apache-2.0

import EntityId from '../entityId';

/**
 * Assessed custom fee view model
 */
class AssessedCustomFeeViewModel {
  /**
   * Constructs the assessed custom fee view model
   *
   * @param {AssessedCustomFee} assessedCustomFee
   */
  constructor(assessedCustomFee) {
    this.amount = assessedCustomFee.amount;
    this.collector_account_id = EntityId.parse(assessedCustomFee.collectorAccountId).toString();
    this.token_id = EntityId.parse(assessedCustomFee.tokenId, {isNullable: true}).toString();

    if (assessedCustomFee.effectivePayerAccountIds != null) {
      this.effective_payer_account_ids = assessedCustomFee.effectivePayerAccountIds.map((payer) =>
        EntityId.parse(payer).toString()
      );
    } else {
      this.effective_payer_account_ids = [];
    }
  }
}

export default AssessedCustomFeeViewModel;
