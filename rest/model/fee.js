// SPDX-License-Identifier: Apache-2.0

class Fee {
  constructor(fee) {
    this.allCollectorsAreExempt = fee.all_collectors_are_exempt;
    this.collectorAccountId = fee.collector_account_id;
  }

  static ALL_COLLECTORS_ARE_EXEMPT = 'all_collectors_are_exempt';
  static COLLECTOR_ACCOUNT_ID = `collector_account_id`;
}

export default Fee;
