// SPDX-License-Identifier: Apache-2.0

class CustomFeeLimitsViewModel {
  /**
   * Formats the CustomFeeLimit data for API response.
   * @param {CustomFeeLimits} customFeeLimits - Array of parsed CustomFeeLimit objects.
   */
  constructor(customFeeLimits) {
    this.max_custom_fees = customFeeLimits.fees.flatMap((fee) => {
      const accountId = this._formatAccountId(fee.accountId);
      return (fee.fees ?? []).map((fixedFee) => ({
        account_id: accountId,
        amount: BigInt(fixedFee.amount),
        denominating_token_id: this._formatTokenId(fixedFee.denominatingTokenId),
      }));
    });
  }

  _formatAccountId(accountId) {
    if (!accountId) {
      return null;
    }

    const acc = accountId.account;
    if (acc?.case === 'accountNum') {
      return `${accountId.shardNum}.${accountId.realmNum}.${acc.value}`;
    }

    return null;
  }

  _formatTokenId(tokenId) {
    if (!tokenId) {
      return null;
    }

    const num = tokenId.tokenNum;
    if (num === 0) {
      return null;
    }

    return `${tokenId.shardNum}.${tokenId.realmNum}.${tokenId.tokenNum}`;
  }
}

export default CustomFeeLimitsViewModel;
