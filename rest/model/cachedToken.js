// SPDX-License-Identifier: Apache-2.0

/**
 * Cached token object to store token's decimals, freeze status and kyc status
 */
class CachedToken {
  constructor(token) {
    this.decimals = token.decimals;
    this.freezeStatus = token.freeze_status;
    this.kycStatus = token.kyc_status;
  }
}

export default CachedToken;
