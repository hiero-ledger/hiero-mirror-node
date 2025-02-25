// SPDX-License-Identifier: Apache-2.0

import EntityId from '../entityId';

/**
 * Custom fee view model
 */
class CustomFeeViewModel {
  /**
   * Constructs custom fee view model
   *
   * @param {CustomFee} customFee
   */
  constructor(customFee) {
    this.created_timestamp = customFee.createdTimestamp;
    this.fixed_fees = customFee.fixedFees?.map((f) => this._parseFixedFee(f)) ?? [];
    this.fractional_fees = customFee.fractionalFees?.map((f) => this._parseFractionalFee(f, customFee.tokenId)) ?? [];
    this.royalty_fees = customFee.royaltyFees?.map((f) => this._parseRoyaltyFee(f)) ?? [];
  }

  _parseFixedFee(fixedFee) {
    return {
      all_collectors_are_exempt: fixedFee.allCollectorsAreExempt ?? false,
      amount: fixedFee.amount,
      collector_account_id: EntityId.parse(fixedFee.collectorAccountId).toString(),
      denominating_token_id: EntityId.parse(fixedFee.denominatingTokenId, {isNullable: true}).toString(),
    };
  }

  _parseFractionalFee(fractionalFee, tokenId) {
    return {
      all_collectors_are_exempt: fractionalFee.allCollectorsAreExempt ?? false,
      amount: {
        numerator: fractionalFee.numerator,
        denominator: fractionalFee.denominator,
      },
      collector_account_id: EntityId.parse(fractionalFee.collectorAccountId).toString(),
      denominating_token_id: EntityId.parse(tokenId).toString(),
      maximum: fractionalFee.maximumAmount ?? null,
      minimum: fractionalFee.minimumAmount,
      net_of_transfers: fractionalFee.netOfTransfers ?? false,
    };
  }

  _parseRoyaltyFee(royaltyFee) {
    const fallback_fee = royaltyFee.fallbackFee?.amount
      ? {
          amount: royaltyFee.fallbackFee.amount,
          denominating_token_id: EntityId.parse(royaltyFee.fallbackFee.denominatingTokenId, {
            isNullable: true,
          }).toString(),
        }
      : null;

    return {
      all_collectors_are_exempt: royaltyFee.allCollectorsAreExempt ?? false,
      amount: {
        denominator: royaltyFee.denominator,
        numerator: royaltyFee.numerator,
      },
      collector_account_id: EntityId.parse(royaltyFee.collectorAccountId).toString(),
      fallback_fee,
    };
  }
}

export default CustomFeeViewModel;
