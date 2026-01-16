// SPDX-License-Identifier: Apache-2.0

class ContractTransaction {
  static CONSENSUS_TIMESTAMP = 'consensus_timestamp';
  static ENTITY_ID = 'entity_id';
  static CONTRACT_IDS = 'contract_ids';
  static PAYER_ACCOUNT_ID = 'payer_account_id';
  static tableName = 'contract_transaction';

  /**
   * Parses contract_transaction table columns into object
   */
  constructor(contractTransaction) {
    this.consensusTimestamp = contractTransaction[ContractTransaction.CONSENSUS_TIMESTAMP];
    this.entityId = contractTransaction[ContractTransaction.ENTITY_ID];
    this.contractIds = contractTransaction[ContractTransaction.CONTRACT_IDS] || [];
    this.payerAccountId = contractTransaction[ContractTransaction.PAYER_ACCOUNT_ID];
  }
}
export default ContractTransaction;
