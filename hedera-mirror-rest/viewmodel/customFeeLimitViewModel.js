// SPDX-License-Identifier: Apache-2.0

class CustomFeeLimitViewModel {
  /**
   * Formats the CustomFeeLimit data for API response.
   * @param {CustomFeeLimits} customFeeLimits - Array of parsed CustomFeeLimit objects.
   */
  constructor(customFeeLimits) {
    this.max_custom_fees = customFeeLimits.fees.flatMap((fee) => {
      const formattedAccountId = this._formatAccountId(fee.accountId);
      (fee.fees ?? []).map((fixedFee) => ({
        account_id: formattedAccountId,
        amount: BigInt(fixedFee.amount),
        denominating_token_id: this._formatTokenId(fixedFee.denominating_token_id),
      }));
    });
  }

  _formatAccountId(accountId) {
    if (!accountId) return null;
    return `${accountId.shardNum}.${accountId.realmNum}.${accountId.accountNum}`;
  }

  _formatTokenId(tokenId) {
    if (!tokenId) return null;
    return `${tokenId.shardNum}.${tokenId.realmNum}.${tokenId.tokenNum}`;
  }
}

export default CustomFeeLimitViewModel;
