// SPDX-License-Identifier: Apache-2.0

import {filterKeys} from '../constants';
import CustomFeeLimits from './customFeeLimits';
import NftTransfer from './nftTransfer';

class Transaction {
  static BASE64_HASH_SIZE = 64;
  static HEX_HASH_SIZE = 96;
  static HEX_HASH_WITH_PREFIX_SIZE = this.HEX_HASH_SIZE + 2;

  static tableAlias = 't';
  static tableName = 'transaction';

  static BATCH_KEY = `batch_key`;
  static CHARGED_TX_FEE = `charged_tx_fee`;
  static CONSENSUS_TIMESTAMP = `consensus_timestamp`;
  static ENTITY_ID = `entity_id`;
  static INITIAL_BALANCE = `initial_balance`;
  static INNER_TRANSACTIONS = `inner_transactions`;
  static MAX_FEE = `max_fee`;
  static MAX_CUSTOM_FEES = `max_custom_fees`;
  static MEMO = `memo`;
  static NFT_TRANSFER = 'nft_transfer';
  static NODE_ACCOUNT_ID = `node_account_id`;
  static NONCE = `nonce`;
  static PARENT_CONSENSUS_TIMESTAMP = `parent_consensus_timestamp`;
  static PAYER_ACCOUNT_ID = `payer_account_id`;
  static RESULT = `result`;
  static SCHEDULED = `scheduled`;
  static TRANSACTION_HASH = `transaction_hash`;
  static TRANSACTION_BYTES = `transaction_bytes`;
  static TYPE = `type`;
  static VALID_DURATION_SECONDS = `valid_duration_seconds`;
  static VALID_START_NS = `valid_start_ns`;
  static INDEX = `index`;
  static FILTER_MAP = {
    [filterKeys.TIMESTAMP]: Transaction.getFullName(Transaction.CONSENSUS_TIMESTAMP),
  };

  /**
   * Parses transaction table columns into object
   */
  constructor(transaction) {
    this.batchKey = transaction.batch_key;
    this.chargedTxFee = transaction.charged_tx_fee;
    this.consensusTimestamp = transaction.consensus_timestamp;
    this.entityId = transaction.entity_id;
    this.initialBalance = transaction.initial_balance;
    this.innerTransactions = transaction.inner_transactions;
    this.maxCustomFees = new CustomFeeLimits(transaction.max_custom_fees).fees;
    this.maxFee = transaction.max_fee;
    this.memo = transaction.memo;
    this.nftTransfer = (transaction.nft_transfer ?? []).map((n) => new NftTransfer(n));
    this.nodeAccountId = transaction.node_account_id;
    this.nonce = transaction.nonce;
    this.parentConsensusTimestamp = transaction.parent_consensus_timestamp;
    this.payerAccountId = transaction.payer_account_id;
    this.result = transaction.result;
    this.scheduled = transaction.scheduled;
    this.transactionHash = transaction.transaction_hash;
    this.transactionBytes = transaction.transaction_bytes;
    this.type = transaction.type;
    this.validDurationSeconds = transaction.valid_duration_seconds;
    this.validStartNs = transaction.valid_start_ns;
    this.index = transaction.index;
  }

  /**
   * Gets full column name with table alias prepended.
   *
   * @param {string} columnName
   * @private
   */
  static getFullName(columnName) {
    return `${this.tableAlias}.${columnName}`;
  }
}

export default Transaction;
