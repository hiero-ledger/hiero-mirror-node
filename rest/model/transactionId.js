// SPDX-License-Identifier: Apache-2.0

class TransactionId {
  constructor(payerAccountId, validStartTimestamp, nonce, scheduled) {
    this.payerAccountId = payerAccountId;
    this.nonce = nonce;
    this.scheduled = scheduled;
    this.validStartTimestamp = validStartTimestamp;
  }
}

export default TransactionId;
