// SPDX-License-Identifier: Apache-2.0

class ContractTransactionHash {
  static CONSENSUS_TIMESTAMP = 'consensus_timestamp';
  static ENTITY_ID = 'entity_id';
  static HASH = 'hash';
  static PAYER_ACCOUNT_ID = 'payer_account_id';
  static TRANSACTION_RESULT = 'transaction_result';
  static tableName = 'contract_transaction_hash';

  /**
   * Parses contract_transaction_hash table columns into object
   */
  constructor(transactionHash) {
    this.consensusTimestamp = transactionHash[ContractTransactionHash.CONSENSUS_TIMESTAMP];
    this.entityId = transactionHash[ContractTransactionHash.ENTITY_ID];
    this.hash = transactionHash[ContractTransactionHash.HASH];
    this.payerAccountId = transactionHash[ContractTransactionHash.PAYER_ACCOUNT_ID];
    this.transactionResult = transactionHash[ContractTransactionHash.TRANSACTION_RESULT];
  }
}

export default ContractTransactionHash;
