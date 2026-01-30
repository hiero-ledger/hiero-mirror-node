// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';

import {Cache} from './cache';
import config from './config';
import * as constants from './constants';
import EntityId from './entityId';
import {InvalidArgumentError, NotFoundError} from './errors';
import {bindTimestampRange} from './timestampRange';
import {getTransactionHash, isValidTransactionHash} from './transactionHash';
import TransactionId from './transactionId';
import * as utils from './utils';

import {
  AssessedCustomFee,
  CryptoTransfer,
  CustomFeeLimits,
  NftTransfer,
  StakingRewardTransfer,
  TokenTransfer,
  Transaction,
  TransactionResult,
  TransactionType,
} from './model';

import {AssessedCustomFeeViewModel, CustomFeeLimitsViewModel, NftTransferViewModel} from './viewmodel';

const SUCCESS_PROTO_IDS = TransactionResult.getSuccessProtoIds();

const {
  query: {
    maxScheduledTransactionConsensusTimestampRangeNs,
    maxTransactionConsensusTimestampRangeNs,
    transactions: {precedingTransactionTypes},
  },
  response: {
    limit: {default: defaultResponseLimit},
  },
} = config;

const cache = new Cache();

const scheduleCreateProtoId = 42;
const SHORTER_CACHE_CONTROL_HEADER = {'cache-control': `public, max-age=5`};

const transactionFields = [
  Transaction.BATCH_KEY,
  Transaction.CHARGED_TX_FEE,
  Transaction.CONSENSUS_TIMESTAMP,
  Transaction.ENTITY_ID,
  Transaction.HIGH_VOLUME,
  Transaction.INNER_TRANSACTIONS,
  Transaction.MAX_CUSTOM_FEES,
  Transaction.MAX_FEE,
  Transaction.MEMO,
  Transaction.NFT_TRANSFER,
  Transaction.NODE_ACCOUNT_ID,
  Transaction.NONCE,
  Transaction.PARENT_CONSENSUS_TIMESTAMP,
  Transaction.PAYER_ACCOUNT_ID,
  Transaction.RESULT,
  Transaction.SCHEDULED,
  Transaction.TRANSACTION_BYTES,
  Transaction.TRANSACTION_HASH,
  Transaction.TYPE,
  Transaction.VALID_DURATION_SECONDS,
  Transaction.VALID_START_NS,
  Transaction.INDEX,
];

const transactionFullFields = transactionFields.map((f) => Transaction.getFullName(f));

const cryptoTransferJsonAgg = `jsonb_agg(jsonb_build_object(
    '${CryptoTransfer.AMOUNT}', ${CryptoTransfer.AMOUNT},
    '${CryptoTransfer.ENTITY_ID}', ${CryptoTransfer.ENTITY_ID},
    '${CryptoTransfer.IS_APPROVAL}', ${CryptoTransfer.IS_APPROVAL}
  ) order by ${CryptoTransfer.ENTITY_ID}, ${CryptoTransfer.AMOUNT})`;

const tokenTransferJsonAgg = `jsonb_agg(jsonb_build_object(
    '${TokenTransfer.ACCOUNT_ID}', ${TokenTransfer.ACCOUNT_ID},
    '${TokenTransfer.AMOUNT}', ${TokenTransfer.AMOUNT},
    '${TokenTransfer.TOKEN_ID}', ${TokenTransfer.TOKEN_ID},
    '${TokenTransfer.IS_APPROVAL}', ${TokenTransfer.IS_APPROVAL}
  ) order by ${TokenTransfer.TOKEN_ID}, ${TokenTransfer.ACCOUNT_ID})`;

const assessedCustomFeeJsonAgg = `jsonb_agg(jsonb_build_object(
    '${AssessedCustomFee.AMOUNT}', ${AssessedCustomFee.AMOUNT},
    '${AssessedCustomFee.COLLECTOR_ACCOUNT_ID}', ${AssessedCustomFee.COLLECTOR_ACCOUNT_ID},
    '${AssessedCustomFee.EFFECTIVE_PAYER_ACCOUNT_IDS}', ${AssessedCustomFee.EFFECTIVE_PAYER_ACCOUNT_IDS},
    '${AssessedCustomFee.TOKEN_ID}', ${AssessedCustomFee.TOKEN_ID}
  ) order by ${AssessedCustomFee.COLLECTOR_ACCOUNT_ID}, ${AssessedCustomFee.AMOUNT})`;

/**
 * Creates an assessed custom fee list from aggregated array of JSON objects in the query result
 *
 * @param assessedCustomFees assessed custom fees
 * @return {undefined|{amount: Number, collector_account_id: string, payer_account_id: string, token_id: string}[]}
 */
const createAssessedCustomFeeList = (assessedCustomFees) => {
  if (!assessedCustomFees) {
    return undefined;
  }

  return assessedCustomFees.map((assessedCustomFee) => {
    const model = new AssessedCustomFee(assessedCustomFee);
    return new AssessedCustomFeeViewModel(model);
  });
};

/**
 * Creates crypto transfer list from aggregated array of JSON objects in the query result. Note if the
 * cryptoTransferList is undefined, an empty array is returned.
 *
 * @param cryptoTransferList crypto transfer list
 * @return {{account: string, amount: Number}[]}
 */
const createCryptoTransferList = (cryptoTransferList) => {
  if (!cryptoTransferList) {
    return [];
  }

  return cryptoTransferList.map((transfer) => {
    const {entity_id: accountId, amount, is_approval} = transfer;
    return {
      account: EntityId.parse(accountId).toString(),
      amount,
      is_approval: _.isNil(is_approval) ? false : is_approval,
    };
  });
};

/**
 * Creates token transfer list from aggregated array of JSON objects in the query result
 *
 * @param tokenTransferList token transfer list
 * @return {undefined|{amount: Number, account: string, token_id: string}[]}
 */
const createTokenTransferList = (tokenTransferList) => {
  if (!tokenTransferList) {
    return [];
  }

  return tokenTransferList.map((transfer) => {
    const {token_id: tokenId, account_id: accountId, amount, is_approval} = transfer;
    return {
      token_id: EntityId.parse(tokenId).toString(),
      account: EntityId.parse(accountId).toString(),
      amount,
      is_approval: _.isNil(is_approval) ? false : is_approval,
    };
  });
};

/**
 * Creates an nft transfer list from aggregated array of JSON objects in the query result
 *
 * @param nftTransferList nft transfer list
 * @return {undefined|{receiver_account_id: string, sender_account_id: string, serial_number: Number, token_id: string}[]}
 */
const createNftTransferList = (nftTransferList) => {
  if (!nftTransferList) {
    return [];
  }

  return nftTransferList.map((transfer) => {
    const nftTransfer = new NftTransfer(transfer);
    return new NftTransferViewModel(nftTransfer);
  });
};

/**
 * Creates a custom fee limit list
 *
 * @param {Buffer[]} maxCustomFeesList - The list of protobuf serialized bytes of proto.CustomFeeLimit
 * @return An array of custom fee limit view models
 */
const createMaxCustomFeesList = (maxCustomFeesList) => {
  return new CustomFeeLimitsViewModel(new CustomFeeLimits(maxCustomFeesList)).max_custom_fees;
};

/**
 * Format the output of the SQL query as an array of transaction objects per the view model.
 *
 * @param rows Array of rows returned as a result of the SQL query
 * @return An array of transaction objects
 */
const formatTransactionRows = async (rows) => {
  const stakingRewardMap = await createStakingRewardTransferList(rows);
  return rows.map((row) => {
    const validStartTimestamp = row.valid_start_ns;
    const payerAccountId = EntityId.parse(row.payer_account_id).toString();

    return {
      assessed_custom_fees: createAssessedCustomFeeList(row.assessed_custom_fees),
      batch_key: utils.encodeKey(row.batch_key),
      bytes: utils.encodeBase64(row.transaction_bytes),
      charged_tx_fee: row.charged_tx_fee,
      consensus_timestamp: utils.nsToSecNs(row.consensus_timestamp),
      entity_id: EntityId.parse(row.entity_id, {isNullable: true}).toString(),
      high_volume: row.high_volume,
      max_fee: utils.getNullableNumber(row.max_fee),
      max_custom_fees: createMaxCustomFeesList(row.max_custom_fees),
      memo_base64: utils.encodeBase64(row.memo),
      name: TransactionType.getName(row.type),
      nft_transfers: createNftTransferList(row.nft_transfer),
      node: EntityId.parse(row.node_account_id, {isNullable: true}).toString(),
      nonce: row.nonce,
      parent_consensus_timestamp: utils.nsToSecNs(row.parent_consensus_timestamp),
      result: TransactionResult.getName(row.result),
      scheduled: row.scheduled,
      staking_reward_transfers: stakingRewardMap.get(row.consensus_timestamp) || [],
      token_transfers: createTokenTransferList(row.token_transfer_list),
      transaction_hash: utils.encodeBase64(row.transaction_hash),
      transaction_id: utils.createTransactionId(payerAccountId, validStartTimestamp),
      transfers: createCryptoTransferList(row.crypto_transfer_list),
      valid_duration_seconds: utils.getNullableNumber(row.valid_duration_seconds),
      valid_start_timestamp: utils.nsToSecNs(validStartTimestamp),
    };
  });
};

/**
 * Gets consensus_timestamps of the transactions that include a transfer from the staking reward account 800
 * transfers are found, the staking_reward_transfers property will be added to the associated transaction object.
 *
 * @param transactions transactions list
 * @return {[]|{number}[]}
 */
const getStakingRewardTimestamps = (transactions) => {
  return transactions
    .filter(
      (transaction) =>
        !_.isNil(transaction.crypto_transfer_list) &&
        transaction.crypto_transfer_list.some(
          (cryptoTransfer) => cryptoTransfer.entity_id === EntityId.systemEntity.stakingRewardAccount.getEncodedId()
        )
    )
    .map((transaction) => transaction.consensus_timestamp);
};

/**
 * Queries for the staking reward transfer list from the transfer list. If staking reward
 * transfers are found, the staking_reward_transfers property will be added to the associated transaction object.
 *
 * @param transactions transactions list
 * @return {Map<consensus_timestamp, staking_reward_transfer>} Map<ConsensusTimestamp, StakingRewardTransfer>
 */
const createStakingRewardTransferList = async (transactions) => {
  const stakingRewardTimestamps = getStakingRewardTimestamps(transactions);
  const rows = await getStakingRewardTransferList(stakingRewardTimestamps);
  return convertStakingRewardTransfers(rows);
};

/**
 * Queries for the staking reward transfer list
 *
 * @param {[]|{number}[]} stakingRewardTimestamps
 * @return rows
 */
const getStakingRewardTransferList = async (stakingRewardTimestamps) => {
  if (stakingRewardTimestamps.length === 0) {
    return [];
  }

  const positions = _.range(1, stakingRewardTimestamps.length + 1).map((position) => `$${position}`);
  const query = `
      select ${StakingRewardTransfer.CONSENSUS_TIMESTAMP},
             json_agg(json_build_object(
                     'account', ${StakingRewardTransfer.ACCOUNT_ID},
                     '${StakingRewardTransfer.AMOUNT}', ${StakingRewardTransfer.AMOUNT})) as staking_reward_transfers
      from ${StakingRewardTransfer.tableName}
      where ${StakingRewardTransfer.CONSENSUS_TIMESTAMP} in (${positions})
      group by ${StakingRewardTransfer.CONSENSUS_TIMESTAMP}`;

  const {rows} = await pool.queryQuietly(query, stakingRewardTimestamps);
  return rows;
};

/**
 * Convert db rows to staking_reward_transfer objects
 * @param rows
 * @returns {Map<any, any>}
 */
const convertStakingRewardTransfers = (rows) => {
  const rewardsMap = new Map();
  rows.forEach((t) => {
    t.staking_reward_transfers.forEach((transfer) => {
      transfer.account = EntityId.parse(transfer.account).toString();
    });
    rewardsMap.set(t.consensus_timestamp, t.staking_reward_transfers || []);
  });
  return rewardsMap;
};

/**
 * Build the where clause from an array of query conditions
 *
 * @param {string} conditions Query conditions
 * @return {string} The where clause built from the query conditions
 */
const buildWhereClause = function (...conditions) {
  const clause = conditions.filter((q) => q !== '').join(' and ');
  return clause === '' ? '' : `where ${clause}`;
};

/**
 * Get the query to find distinct timestamps for transactions matching the query filters from a transfers table when filtering by an account id
 *
 * @param {string} tableName - Name of the transfers table to query
 * @param {string} tableAlias - Alias to reference the table by
 * @param timestampQuery - Transaction table timestamp query with named parameters
 * @param resultTypeQuery - Transaction result query
 * @param transactionTypeQuery - Transaction type query
 * @param accountQuery - Account query with named parameters
 * @param creditDebitQuery - Credit/debit query
 * @param order - Sorting order
 * @param limitQuery - Limit query with named parameters
 * @return {string} - The distinct timestamp query for the given table
 */
const getTransferDistinctTimestampsQuery = (
  tableName,
  tableAlias,
  timestampQuery,
  resultTypeQuery,
  transactionTypeQuery,
  accountQuery,
  creditDebitQuery,
  order,
  limitQuery
) => {
  const fullTimestampColumn = `${tableAlias}.consensus_timestamp`;
  const fullPayerAccountIdColumn = `${tableAlias}.payer_account_id`;
  const transferTimestampQuery = timestampQuery.replace(/t\.consensus_timestamp/g, `${fullTimestampColumn}`);
  const joinClause =
    (resultTypeQuery || transactionTypeQuery) &&
    `join ${Transaction.tableName} as ${Transaction.tableAlias}
      using (${Transaction.CONSENSUS_TIMESTAMP}, ${Transaction.PAYER_ACCOUNT_ID})`;
  const whereClause = buildWhereClause(
    accountQuery,
    transferTimestampQuery,
    resultTypeQuery,
    transactionTypeQuery,
    creditDebitQuery
  );

  return `
      select distinct on (${fullTimestampColumn}) ${fullTimestampColumn}      as consensus_timestamp,
                                                  ${fullPayerAccountIdColumn} as payer_account_id
      from ${tableName} as ${tableAlias} ${joinClause} ${whereClause}
      order by ${fullTimestampColumn} ${order} ${limitQuery}`;
};

// the condition to exclude synthetic transactions attached to a user submitted transaction
const transactionByPayerExcludeSyntheticCondition = `${Transaction.getFullName(Transaction.NONCE)} = 0 or
  ${Transaction.getFullName(Transaction.PARENT_CONSENSUS_TIMESTAMP)} is not null
${
  precedingTransactionTypes.length > 0
    ? `or ${Transaction.getFullName(Transaction.TYPE)} in (${precedingTransactionTypes.join(', ')})`
    : ''
}`;

const getQueryWithEqualValues = (column, params, values) => {
  if (values.length === 0) {
    return '';
  }

  return values.length === 1 ? `${column} = $${params.push(values[0])}` : `${column} = any($${params.push(values)})`;
};

const extractSqlFromTransactionsRequest = (filters) => {
  const accountConditions = [];
  const accountIdEqValues = [];
  let creditDebitQuery = '';
  let lastCreditDebitValue = null;
  let limit = defaultResponseLimit;
  let order = constants.orderFilterValues.DESC;
  const params = [];
  let resultType = null;
  let resultTypeQuery = '';
  const transactionTypes = [];

  for (const filter of filters) {
    const {key, operator, value} = filter;
    switch (key) {
      case constants.filterKeys.ACCOUNT_ID:
        if (operator === utils.opsMap.eq) {
          accountIdEqValues.push(value);
        } else {
          accountConditions.push(`ctl.entity_id${operator}$${params.push(value)}`);
        }
        break;
      case constants.filterKeys.CREDIT_TYPE:
        if (lastCreditDebitValue !== null && lastCreditDebitValue !== value) {
          return null;
        }
        lastCreditDebitValue = value;
        break;
      case constants.filterKeys.LIMIT:
        limit = value;
        break;
      case constants.filterKeys.ORDER:
        order = value;
        break;
      case constants.filterKeys.RESULT:
        resultType = value;
        break;
      case constants.filterKeys.TRANSACTION_TYPE:
        transactionTypes.push(TransactionType.getProtoId(value));
        break;
    }
  }

  accountConditions.push(getQueryWithEqualValues('ctl.entity_id', params, accountIdEqValues));
  const accountQuery = accountConditions.filter(Boolean).join(' and ');

  if (lastCreditDebitValue) {
    const operator = lastCreditDebitValue.toLowerCase() === constants.cryptoTransferType.CREDIT ? '>' : '<';
    creditDebitQuery = `ctl.amount ${operator} 0`;
  }

  if (resultType) {
    const operator = resultType === constants.transactionResultFilter.SUCCESS ? 'in' : 'not in';
    resultTypeQuery = `t.result ${operator} (${utils.resultSuccess})`;
  }

  const transactionTypeQuery = getQueryWithEqualValues('type', params, transactionTypes);
  const limitQuery = `limit $${params.push(limit)}`;

  return {
    accountQuery,
    creditDebitQuery,
    limit,
    limitQuery,
    order,
    params,
    resultTypeQuery,
    transactionTypeQuery,
  };
};

/**
 * @param filters The filters from the http request
 * @param timestampRange the timestamp range object
 * @return {Object} the object for obtaining the results of the query
 */
const getTransactionTimestamps = async (filters, timestampRange) => {
  if (timestampRange.eqValues.length > 1 || timestampRange.range?.isEmpty()) {
    return {rows: []};
  }

  const result = extractSqlFromTransactionsRequest(filters);
  if (result === null) {
    return {rows: []};
  }
  const {accountQuery, creditDebitQuery, limit, limitQuery, order, resultTypeQuery, transactionTypeQuery, params} =
    result;

  let nextTimestamp;
  if (timestampRange.eqValues.length === 0) {
    const {range, next} = await bindTimestampRange(timestampRange.range, order);
    timestampRange.range = range;
    nextTimestamp = next;
  }

  let [timestampQuery, timestampParams] = utils.buildTimestampQuery('t.consensus_timestamp', timestampRange);
  timestampQuery = utils.convertMySqlStyleQueryToPostgres(timestampQuery, params.length + 1);
  params.push(...timestampParams);

  const query = getTransactionTimestampsQuery(
    accountQuery,
    timestampQuery,
    resultTypeQuery,
    limitQuery,
    creditDebitQuery,
    transactionTypeQuery,
    order
  );
  const {rows} = await pool.queryQuietly(query, params);

  return {limit, order, nextTimestamp, rows};
};

/**
 *
 *
 * @param {String} accountQuery SQL query that filters based on the account ids
 * @param {String} timestampQuery SQL query that filters based on the timestamps for transaction table
 * @param {String} resultTypeQuery SQL query that filters based on the result types
 * @param {String} limitQuery SQL query that limits the number of unique transactions returned
 * @param {String} creditDebitQuery SQL query that filters for credit/debit transactions
 * @param {String} transactionTypeQuery SQL query that filters by transaction type
 * @param {String} order Sorting order
 * @return {String} query to retrieve relevant timestamp and payer ID information
 */
const getTransactionTimestampsQuery = (
  accountQuery,
  timestampQuery,
  resultTypeQuery,
  limitQuery,
  creditDebitQuery,
  transactionTypeQuery,
  order
) => {
  const transactionAccountQuery = accountQuery
    ? `${accountQuery.replace(/ctl\.entity_id/g, 't.payer_account_id')}
      and (${transactionByPayerExcludeSyntheticCondition})`
    : '';
  const transactionWhereClause = buildWhereClause(
    transactionAccountQuery,
    timestampQuery,
    resultTypeQuery,
    transactionTypeQuery
  );
  const transactionOnlyQuery = `select ${Transaction.CONSENSUS_TIMESTAMP}, ${Transaction.PAYER_ACCOUNT_ID}
                                from ${Transaction.tableName} as ${Transaction.tableAlias} ${transactionWhereClause}
                                order by ${Transaction.getFullName(
                                  Transaction.CONSENSUS_TIMESTAMP
                                )} ${order} ${limitQuery}`;

  if (creditDebitQuery || accountQuery) {
    const cryptoTransferQuery = getTransferDistinctTimestampsQuery(
      CryptoTransfer.tableName,
      'ctl',
      timestampQuery,
      resultTypeQuery,
      transactionTypeQuery,
      accountQuery,
      creditDebitQuery,
      order,
      limitQuery
    );

    const tokenTransferQuery = getTransferDistinctTimestampsQuery(
      TokenTransfer.tableName,
      'ttl',
      timestampQuery,
      resultTypeQuery,
      transactionTypeQuery,
      accountQuery.replace(/ctl\.entity_id/g, 'ttl.account_id'),
      creditDebitQuery.replace(/ctl\.amount/g, 'ttl.amount'),
      order,
      limitQuery
    );

    if (creditDebitQuery) {
      // credit/debit filter applies to crypto_transfer.amount and token_transfer.amount, a full outer join is needed to get
      // transactions that only have a crypto_transfer or a token_transfer
      return `
          select coalesce(ctl.consensus_timestamp, ttl.consensus_timestamp) as consensus_timestamp,
                 coalesce(ctl.payer_account_id, ttl.payer_account_id)       as payer_account_id
          from (${cryptoTransferQuery}) as ctl
                   full outer join (${tokenTransferQuery}) as ttl
                                   on ctl.consensus_timestamp = ttl.consensus_timestamp
          order by consensus_timestamp ${order}
              ${limitQuery}`;
    }

    // account filter applies to transaction.payer_account_id, crypto_transfer.entity_id,
    // and token_transfer.account_id, a full outer join between the four tables is needed to get rows that may only exist in one.
    return `
        select coalesce(t.consensus_timestamp, ctl.consensus_timestamp, ttl.consensus_timestamp) as consensus_timestamp,
               coalesce(t.payer_account_id, ctl.payer_account_id, ttl.payer_account_id)          as payer_account_id
        from (${transactionOnlyQuery}) as t
                 full outer join (${cryptoTransferQuery}) as ctl
                                 on t.consensus_timestamp = ctl.consensus_timestamp
                 full outer join (${tokenTransferQuery}) as ttl
                                 on coalesce(t.consensus_timestamp, ctl.consensus_timestamp) = ttl.consensus_timestamp
        order by consensus_timestamp ${order}
            ${limitQuery}`;
  }

  return transactionOnlyQuery;
};

/**
 * Get the transaction details given the payer account ids and consensus timestamps
 *
 * @param {{Object}[]} payerAndTimestamps The transaction payer and consensus timestamp pairs
 * @param {String} order Sorting order
 * @return {Promise} Promise returning the transaction details
 */
const getTransactionsDetails = async (payerAndTimestamps, order) => {
  if (payerAndTimestamps.length === 0) {
    return {rows: []};
  }

  const payerAccountIds = new Set();
  const timestamps = [];
  payerAndTimestamps.forEach((row) => {
    timestamps.push(row.consensus_timestamp);
    payerAccountIds.add(row.payer_account_id);
  });

  const params = [];
  const payerAccountIdsCondition = getQueryWithEqualValues('payer_account_id', params, Array.from(payerAccountIds));
  const timestampsCondition = getQueryWithEqualValues('consensus_timestamp', params, timestamps);
  const outerPayerAccountIdsCondition = 't.' + payerAccountIdsCondition;
  const outerTimestampsCondition = 't.' + timestampsCondition;

  const query = `with c_list as (select consensus_timestamp,
                                        payer_account_id,
                                        ${cryptoTransferJsonAgg} as crypto_transfer_list
                                 from crypto_transfer
                                 where ${payerAccountIdsCondition}
                                   and ${timestampsCondition}
                                 group by consensus_timestamp, payer_account_id),
                      t_list as (select consensus_timestamp,
                                        payer_account_id,
                                        ${tokenTransferJsonAgg} as token_transfer_list
                                 from token_transfer
                                 where ${payerAccountIdsCondition}
                                   and ${timestampsCondition}
                                 group by consensus_timestamp, payer_account_id)
                 select ${transactionFullFields},
                        (select crypto_transfer_list
                         from c_list
                         where consensus_timestamp = t.consensus_timestamp
                           and payer_account_id = t.payer_account_id),
                        (select token_transfer_list
                         from t_list
                         where consensus_timestamp = t.consensus_timestamp
                           and payer_account_id = t.payer_account_id)
                 from transaction as t
                 where ${outerPayerAccountIdsCondition}
                   and ${outerTimestampsCondition}
                 order by t.consensus_timestamp ${order}`;

  return pool.queryQuietly(query, params);
};

/**
 * Handler function for /transactions API.
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @return {Promise}
 */
const getTransactions = async (req, res) => {
  const filters = utils.buildAndValidateFilters(req.query, acceptedTransactionParameters);
  const timestampFilters = filters.filter((filter) => filter.key === constants.filterKeys.TIMESTAMP);
  const timestampRange = utils.parseTimestampFilters(timestampFilters, false, true, true, false, false);

  res.locals[constants.responseDataLabel] = await doGetTransactions(filters, req, timestampRange);
};

const keyMapper = (key) => {
  let timestamp = key.consensus_timestamp;
  if (typeof timestamp === 'string') {
    timestamp = timestamp.replace('.', '');
  }
  return `transaction:${timestamp}`;
};

/**
 * Get transactions per the http request.
 *
 * @param {Object[]} filters The filters extracted from the http request
 * @param {Request} req The http request
 * @param timestampRange The timestamp range parsed from the http request
 * @returns {Promise<{links: {next: String}, transactions: *}>}
 */
const doGetTransactions = async (filters, req, timestampRange) => {
  const {
    limit,
    order,
    nextTimestamp,
    rows: payerAndTimestamps,
  } = await getTransactionTimestamps(filters, timestampRange);

  const loader = (keys) => getTransactionsDetails(keys, order).then((result) => formatTransactionRows(result.rows));

  const transactions = await cache.get(payerAndTimestamps, loader, keyMapper);

  const isEnd = transactions.length !== limit;
  const next = utils.getPaginationLink(
    req,
    isEnd && !nextTimestamp,
    {
      [constants.filterKeys.TIMESTAMP]: !isEnd
        ? transactions[transactions.length - 1]?.consensus_timestamp
        : nextTimestamp,
    },
    order
  );

  return {
    transactions,
    links: {next},
  };
};

/**
 * Get the query for either getting transaction by id or getting transaction by payer account id and a list of
 * consensus timestamps
 *
 * @param {string} mainCondition conditions for the main query
 * @param {string} subQueryCondition conditions for the transfer table subquery
 * @return {string} The query
 */
const getTransactionQuery = (mainCondition, subQueryCondition) => {
  return `
      select ${transactionFullFields},
             (select ${cryptoTransferJsonAgg}
              from ${CryptoTransfer.tableName}
              where ${CryptoTransfer.CONSENSUS_TIMESTAMP} = t.consensus_timestamp
                and ${CryptoTransfer.PAYER_ACCOUNT_ID} = t.payer_account_id
                and ${subQueryCondition}) as crypto_transfer_list,
             (select ${tokenTransferJsonAgg}
              from ${TokenTransfer.tableName}
              where ${TokenTransfer.CONSENSUS_TIMESTAMP} = t.consensus_timestamp
                and ${TokenTransfer.PAYER_ACCOUNT_ID} = t.payer_account_id
                and ${subQueryCondition}) as token_transfer_list,
             (select ${assessedCustomFeeJsonAgg}
              from ${AssessedCustomFee.tableName}
              where ${AssessedCustomFee.CONSENSUS_TIMESTAMP} = t.consensus_timestamp
                and ${AssessedCustomFee.PAYER_ACCOUNT_ID} = t.payer_account_id
                and ${subQueryCondition}) as assessed_custom_fees
      from ${Transaction.tableName} ${Transaction.tableAlias}
      where ${mainCondition}
      order by ${Transaction.CONSENSUS_TIMESTAMP}`;
};

/**
 * Extracts the sql query and params for transactions request by transaction id
 *
 * @param {String} transactionIdOrHash
 * @param {Array} filters
 * @return {{query: string, params: *[]}}
 */
const extractSqlFromTransactionsByIdOrHashRequest = async (transactionIdOrHash, filters) => {
  const isTransactionHash = isValidTransactionHash(transactionIdOrHash);

  if (isTransactionHash) {
    const encoding = transactionIdOrHash.length === Transaction.BASE64_HASH_SIZE ? 'base64url' : 'hex';
    if (transactionIdOrHash.length === Transaction.HEX_HASH_WITH_PREFIX_SIZE) {
      transactionIdOrHash = transactionIdOrHash.substring(2);
    }

    const rows = await getTransactionHash(Buffer.from(transactionIdOrHash, encoding));
    if (rows.length === 0) {
      throw new NotFoundError();
    }

    const payerAccountId = rows[0].payer_account_id;
    const lookupKeys = rows.map((row) => [payerAccountId, row.consensus_timestamp]).flat();

    return {
      ...getTransactionsByTransactionIdsSql(lookupKeys, filters, Transaction.CONSENSUS_TIMESTAMP),
      isTransactionHash,
    };
  } else {
    // try to parse it as a transaction id
    const transactionId = TransactionId.fromString(transactionIdOrHash);
    const payerAccountId = BigInt(transactionId.getEntityId().getEncodedId());
    const validStartTimestamp = BigInt(transactionId.getValidStartNs());

    return {
      ...getTransactionsByTransactionIdsSql([payerAccountId, validStartTimestamp], filters, Transaction.VALID_START_NS),
      isTransactionHash,
    };
  }
};

const getTransactionByIdQueryParamConditions = (filters, params) => {
  const conditions = [];
  // only parse nonce and scheduled query filters if the path parameter is transaction id or hash
  let nonce;
  let scheduled;

  for (const filter of filters) {
    // honor the last for both nonce and scheduled
    switch (filter.key) {
      case constants.filterKeys.NONCE:
        nonce = filter.value;
        break;
      case constants.filterKeys.SCHEDULED:
        scheduled = filter.value;
        break;
    }
  }

  if (nonce !== undefined) {
    params.push(nonce);
    conditions.push(`${Transaction.NONCE} = $${params.length}`);
  }

  if (scheduled !== undefined) {
    params.push(scheduled);
    conditions.push(`${Transaction.SCHEDULED} = $${params.length}`);
  }

  return {conditions, scheduled, nonce};
};

const getTransactionsByTransactionIdsSql = (transactionKeys, filters, timestampField) => {
  let payerAccountParams = [];
  let params = [];

  let minTimestamp = constants.MAX_LONG;
  let maxTimestamp = -1;
  let idConditions = [];

  if (transactionKeys.length % 2 !== 0) {
    throw new InvalidArgumentError('transaction keys must be in sequenced pairs of [payer,timestamp]');
  }

  for (let index = 0; index < transactionKeys.length; index += 2) {
    let paramIndex = params.length;
    const payer = transactionKeys[index];
    const timestamp = BigInt(transactionKeys[index + 1]);

    if (payer) {
      payerAccountParams.push(`$${++paramIndex}`);
      idConditions.push(`(${Transaction.PAYER_ACCOUNT_ID} = $${paramIndex} and 
                               ${timestampField} = $${++paramIndex})`);
      params.push(payer);
    } else if (timestampField === Transaction.VALID_START_NS) {
      throw new InvalidArgumentError('payer is required when timestamp is valid_start_ns');
    } else {
      idConditions.push(`${timestampField} = $${++paramIndex}`);
    }
    params.push(timestamp);

    if (timestamp < minTimestamp) {
      minTimestamp = timestamp;
    }
    if (timestamp > maxTimestamp) {
      maxTimestamp = timestamp;
    }
  }

  const maxConsensusTimestamp =
    timestampField === Transaction.CONSENSUS_TIMESTAMP
      ? transactionKeys[transactionKeys.length - 1]
      : maxTimestamp + maxTransactionConsensusTimestampRangeNs;
  const commonConditions = [
    `${Transaction.CONSENSUS_TIMESTAMP} >= $${params.length + 1}`,
    `${Transaction.CONSENSUS_TIMESTAMP} <= $${params.length + 2}`,
  ];
  params.lowerConsensusTimestampIndex = params.length;
  params.push(
    timestampField === Transaction.VALID_START_NS
      ? minTimestamp - config.query.maxValidStartTimestampDriftNs
      : minTimestamp
  );
  params.upperConsensusTimestampIndex = params.length;
  params.push(maxConsensusTimestamp);

  const {conditions: filterConditions, scheduled} = getTransactionByIdQueryParamConditions(filters, params);
  const mainConditions = [...commonConditions, `(${idConditions.join(' or ')})`, ...filterConditions].join(' and ');
  const subqueryConditions = payerAccountParams.length
    ? [...commonConditions, `${Transaction.PAYER_ACCOUNT_ID} in (${payerAccountParams.join(',')})`].join(' and ')
    : commonConditions.join(' and ');

  return {
    query: getTransactionQuery(mainConditions, subqueryConditions),
    params,
    scheduled,
  };
};

const getTransactionsByIdOrHashCacheControlHeader = (isTransactionHash, scheduledParamExists, transactions) => {
  if (isTransactionHash || scheduledParamExists) {
    // If the query uses a transaction hash or a scheduled filter exists, don't override
    return {};
  }

  // Default to no override
  let header = {};
  for (const transaction of transactions) {
    if (transaction.type === scheduleCreateProtoId && SUCCESS_PROTO_IDS.includes(transaction.result)) {
      // SCHEDULECREATE transaction cannot be scheduled
      const elapsed = utils.nowInNs() - transaction.consensus_timestamp;
      if (elapsed < maxScheduledTransactionConsensusTimestampRangeNs) {
        header = SHORTER_CACHE_CONTROL_HEADER;
      }
    } else if (transaction.scheduled) {
      return {};
    }
  }

  return header;
};

/**
 * Handler function for /transactions/:transactionIdOrHash API.
 * @param {Request} req HTTP request object
 * @return {Promise<None>}
 */
const getTransactionsByIdOrHash = async (req, res) => {
  const filters = utils.buildAndValidateFilters(req.query, acceptedSingleTransactionParameters);
  const {query, params, scheduled, isTransactionHash} = await extractSqlFromTransactionsByIdOrHashRequest(
    req.params.transactionIdOrHash,
    filters
  );

  const {rows} = await pool.queryQuietly(query, params).then((result) => {
    if (!mayMissLongTermScheduledTransaction(isTransactionHash, scheduled, result.rows)) {
      return result;
    }

    params[params.upperConsensusTimestampIndex] =
      params[params.lowerConsensusTimestampIndex] + maxScheduledTransactionConsensusTimestampRangeNs;
    return pool.queryQuietly(query, params);
  });

  if (rows.length === 0) {
    throw new NotFoundError();
  }

  const innerTransactions = rows
    .map((row) => row.inner_transactions)
    .filter((innerTransactions) => innerTransactions)
    .flat();

  if (!isTransactionHash && innerTransactions.length > 0) {
    const {query: innerTransactionQuery, params} = getTransactionsByTransactionIdsSql(
      innerTransactions,
      filters,
      Transaction.VALID_START_NS
    );
    const {rows: innerTransactionRows} = await pool.queryQuietly(innerTransactionQuery, params);

    rows.push(...innerTransactionRows);
  }

  const transactions = await formatTransactionRows(rows);

  res.locals[constants.responseHeadersLabel] = getTransactionsByIdOrHashCacheControlHeader(
    isTransactionHash,
    scheduled !== undefined,
    rows
  );

  logger.debug(`getTransactionsByIdOrHash returning ${transactions.length} entries`);
  res.locals[constants.responseDataLabel] = {
    transactions,
  };
};

const mayMissLongTermScheduledTransaction = (isTransactionHash, scheduled, transactions) => {
  // Note scheduled may be undefined
  if (isTransactionHash || scheduled === false) {
    return false;
  }

  if (scheduled === undefined) {
    let scheduleExists = false;
    for (const transaction of transactions) {
      if (transaction.type === scheduleCreateProtoId && SUCCESS_PROTO_IDS.includes(transaction.result)) {
        scheduleExists = true;
      } else if (transaction.scheduled) {
        return false;
      }
    }

    return scheduleExists;
  } else if (scheduled && transactions.length === 0) {
    return true;
  }

  return false;
};

const transactions = {
  doGetTransactions,
  getTransactions,
  getTransactionsByIdOrHash,
};

const acceptedTransactionParameters = new Set([
  constants.filterKeys.ACCOUNT_ID,
  constants.filterKeys.CREDIT_TYPE,
  constants.filterKeys.LIMIT,
  constants.filterKeys.ORDER,
  constants.filterKeys.RESULT,
  constants.filterKeys.TIMESTAMP,
  constants.filterKeys.TRANSACTION_TYPE,
]);

const acceptedSingleTransactionParameters = new Set([constants.filterKeys.NONCE, constants.filterKeys.SCHEDULED]);

if (utils.isTestEnv()) {
  Object.assign(transactions, {
    buildWhereClause,
    convertStakingRewardTransfers,
    createAssessedCustomFeeList,
    createCryptoTransferList,
    createNftTransferList,
    createStakingRewardTransferList,
    createTokenTransferList,
    extractSqlFromTransactionsByIdOrHashRequest,
    extractSqlFromTransactionsRequest,
    formatTransactionRows,
    getStakingRewardTimestamps,
    getTransactionsByIdOrHashCacheControlHeader,
    getTransactionsByTransactionIdsSql,
    isValidTransactionHash,
    mayMissLongTermScheduledTransaction,
  });
}

export default transactions;
