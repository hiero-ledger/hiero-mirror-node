// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';
import base32 from './base32';
import {getResponseLimit} from './config';
import * as constants from './constants';
import {filterKeys} from './constants';
import EntityId from './entityId';
import * as utils from './utils';
import {opsMap} from './utils';
import {EntityService} from './service';
import transactions from './transactions';
import {NotFoundError} from './errors';
import {Entity} from './model';
import balances from './balances';

const {tokenBalance: tokenBalanceResponseLimit} = getResponseLimit();

const getEntityStakeQuery = (filter, isHistorical = false) => {
  if (isHistorical) {
    return `(
      select * from (
          select * from entity_stake as e where ${filter}
                union all
          select * from entity_stake_history as e where ${filter}
      )
      as asd
      order by asd.timestamp_range desc limit 1
    )`;
  }

  return 'entity_stake';
};

/**
 * Processes one row of the results of the SQL query and format into API return format
 * @param {Object} row One row of the SQL query result
 * @return {Object} Processed account record
 */
const processRow = (row) => {
  const alias = base32.encode(row.alias);
  const balance =
    row.balance === undefined
      ? null
      : {
          balance: row.balance,
          timestamp: utils.nsToSecNs(row.balance_timestamp),
          tokens: utils.parseTokenBalances(row.token_balances),
        };
  const entityId = EntityId.parse(row.id);
  let evmAddress = row.evm_address && utils.toHexString(row.evm_address, true);
  if (evmAddress === null) {
    if (alias && row.alias.length === constants.EVM_ADDRESS_LENGTH) {
      evmAddress = utils.toHexString(row.alias, true);
    } else {
      evmAddress = entityId.toEvmAddress();
    }
  }

  let delegationAddress = row.delegation_address && utils.toHexString(row.delegation_address, true);
  if (!delegationAddress || delegationAddress === '0x0000000000000000000000000000000000000000') {
    delegationAddress = '0x';
  }

  const stakedToNode = row.staked_node_id !== null && row.staked_node_id !== -1;
  return {
    account: entityId.toString(),
    alias,
    auto_renew_period: row.auto_renew_period,
    balance,
    created_timestamp: utils.nsToSecNs(row.created_timestamp),
    decline_reward: row.decline_reward,
    delegation_address: delegationAddress,
    deleted: row.deleted,
    ethereum_nonce: row.ethereum_nonce,
    evm_address: evmAddress,
    expiry_timestamp: utils.nsToSecNs(
      utils.calculateExpiryTimestamp(row.auto_renew_period, row.created_timestamp, row.expiration_timestamp)
    ),
    key: utils.encodeKey(row.key),
    max_automatic_token_associations: row.max_automatic_token_associations,
    memo: row.memo,
    pending_reward: row.pending_reward,
    receiver_sig_required: row.receiver_sig_required,
    staked_account_id: EntityId.parse(row.staked_account_id, {isNullable: true}).toString(),
    staked_node_id: stakedToNode ? row.staked_node_id : null,
    stake_period_start:
      stakedToNode && row.stake_period_start !== -1
        ? utils.nsToSecNs(BigInt(row.stake_period_start) * constants.ONE_DAY_IN_NS)
        : null,
  };
};

// 'id' is different for different join types, so will add later when composing the query
const entityFields = [
  'e.alias',
  'e.auto_renew_period',
  'e.created_timestamp',
  'e.decline_reward',
  'e.delegation_address',
  'e.deleted',
  'e.ethereum_nonce',
  'e.evm_address',
  'e.expiration_timestamp',
  'e.id',
  'e.key',
  'e.max_automatic_token_associations',
  'e.memo',
  'e.receiver_sig_required',
  'e.staked_account_id',
  'e.staked_node_id',
  'e.stake_period_start',
  'e.type',
  'e.timestamp_range',
  `(case when es.pending_reward is null then 0
         when e.decline_reward is true or coalesce(e.staked_node_id, -1) = -1 then 0
         when e.stake_period_start >= es.end_stake_period then 0
         else es.pending_reward
    end) as pending_reward`,
].join(',\n');

/**
 * Gets the query for entity fields with hbar and token balance info
 *
 * @param entityBalanceQuery
 * @param entityAccountQuery
 * @param limitAndOrderQuery
 * @param pubKeyQuery
 * @param tokenBalanceQuery
 * @param accountBalanceQuery
 * @param isHistorical whether to query historical data
 * @return {{query: string, params: *[]}}
 */
const getEntityBalanceQuery = (
  entityBalanceQuery,
  entityAccountQuery,
  limitAndOrderQuery,
  pubKeyQuery,
  tokenBalanceQuery,
  accountBalanceQuery,
  isHistorical = false
) => {
  const {query: limitQuery, params: limitParams, order} = limitAndOrderQuery;

  const whereCondition = [
    `e.type in ('ACCOUNT', 'CONTRACT') and NOT(e.num = 2 and e.memo = 'Mirror node created synthetic treasury account')`,
    entityBalanceQuery.query,
    entityAccountQuery.query,
    pubKeyQuery.query,
  ]
    .filter((x) => !!x)
    .join(' and ');
  const params = utils.mergeParams(
    [],
    tokenBalanceQuery.params,
    entityBalanceQuery.params,
    entityAccountQuery.params,
    pubKeyQuery.params,
    accountBalanceQuery.params
  );

  // Need historical balance info if the generated query for account balance is not empty or forced to use balance info
  // from entity / entity_history union. The latter happens when given the timestamp query params in the request, no
  // valid balance timestamp snapshot is found.
  const needHistoricalBalanceInfo = accountBalanceQuery.query || accountBalanceQuery.forceUnionEntityHistory;
  const queries = [];
  let selectTokenBalance;
  if (needHistoricalBalanceInfo) {
    // Return empty array if forceUnionEntityHistory is true, because there is no token balance info wrt the entity
    // balance timestamp
    selectTokenBalance = accountBalanceQuery.query
      ? `(
          select json_agg(jsonb_build_object('token_id', token_id, 'balance', balance)) ::jsonb
          from (
            select distinct on (token_id) token_id, balance
            from token_balance
            where ${tokenBalanceQuery.query}
            order by token_id ${order}, consensus_timestamp desc
            limit ${tokenBalanceQuery.limit}
          ) as account_token_balance
        ) as token_balances`
      : "'[]'::jsonb as token_balances";
  } else {
    queries.push(`with latest_token_balance as (
       select account_id, balance, token_id
       from token_account
       where associated is true)`);
    selectTokenBalance = `(select json_agg(jsonb_build_object('token_id', token_id, 'balance', balance)) ::jsonb
          from (
            select token_id, balance
            from latest_token_balance
            where ${tokenBalanceQuery.query}
            order by token_id ${order}
            limit ${tokenBalanceQuery.limit}
          ) as account_token_balance)
        as token_balances`;
  }

  let balanceField = 'e.balance as balance';
  let balanceTimestampField = 'e.balance_timestamp as balance_timestamp';
  let entityTable;
  let orderClause;
  let whereClause;

  if (needHistoricalBalanceInfo) {
    if (accountBalanceQuery.query) {
      balanceField = `${accountBalanceQuery.query} as balance`;
      balanceTimestampField = `$${accountBalanceQuery.timestampParamIndex} as balance_timestamp`;
    }

    entityTable = `(
        select *
        from ${Entity.tableName} as e
        where ${whereCondition}
        union all
        select *
        from ${Entity.historyTableName} as e
        where ${whereCondition}
        order by ${Entity.TIMESTAMP_RANGE} desc limit 1
      )`;
  } else {
    entityTable = 'entity';
    whereClause = `where ${whereCondition}`;
    orderClause = `order by e.id ${order}`;
    utils.mergeParams(params, limitParams);
  }

  const selectFields = [entityFields, selectTokenBalance, balanceField, balanceTimestampField];
  queries.push(`select ${selectFields.join(',\n')}
    from ${entityTable} as e
    left join
      ${getEntityStakeQuery(entityAccountQuery.query, isHistorical)}
    as es on es.id = e.id ${getEntityStakeAccountCondition(entityAccountQuery)}
    ${[whereClause, orderClause, limitQuery].filter(Boolean).join('\n')}`);
  const query = queries.join('\n');

  return {query, params};
};

const emptyTransactionsPromise = Promise.resolve({transactions: [], links: {next: null}});

/**
 * Creates account query and params from filters with limit and order
 *
 * @param entityAccountQuery entity id query
 * @param tokenBalanceQuery token balance query
 * @param accountBalanceQuery optional query for relevant balance file
 * @param entityBalanceQuery optional account balance query
 * @param limitAndOrderQuery optional limit and order query
 * @param pubKeyQuery optional entity public key query
 * @param includeBalance include balance info or not
 * @param isHistorical whether to query historical data
 * @return {{query: string, params: []}}
 */
const getAccountQuery = (
  entityAccountQuery,
  tokenBalanceQuery,
  accountBalanceQuery = {query: '', params: []},
  entityBalanceQuery = {query: '', params: []},
  limitAndOrderQuery = {query: '', params: [], order: constants.orderFilterValues.ASC},
  pubKeyQuery = {query: '', params: []},
  includeBalance = true,
  isHistorical = false
) => {
  // Keep track of Postgres positional param index when converting MySQL style value placeholder ? to Postgres style
  // $1, $2, .... The query fragments are ordered in the same order as their params
  let paramIndex = 1;

  if (!includeBalance) {
    [entityAccountQuery, pubKeyQuery, limitAndOrderQuery]
      .filter((query) => !!query.query)
      .forEach((query) => {
        query.query = query.query.replace(/\?/g, (s) => `$${paramIndex++}`);
      });
    const entityCondition = [`e.type in ('ACCOUNT', 'CONTRACT')`, entityAccountQuery.query, pubKeyQuery.query]
      .filter((x) => !!x)
      .join(' and ');
    const entityOnlyQuery = `
            select ${entityFields}
            from entity e
              left join entity_stake es on es.id = e.id ${getEntityStakeAccountCondition(entityAccountQuery)}
            where ${entityCondition}
            order by id ${limitAndOrderQuery.order} ${limitAndOrderQuery.query}`;
    return {
      query: entityOnlyQuery,
      params: utils.mergeParams(entityAccountQuery.params, pubKeyQuery.params, limitAndOrderQuery.params),
    };
  }

  [tokenBalanceQuery, entityBalanceQuery, entityAccountQuery, pubKeyQuery, accountBalanceQuery, limitAndOrderQuery]
    .filter((query) => !!query.query)
    .forEach((query) => {
      query.query = query.query.replace(/\?/g, (s) => `$${paramIndex++}`);
    });

  return getEntityBalanceQuery(
    entityBalanceQuery,
    entityAccountQuery,
    limitAndOrderQuery,
    pubKeyQuery,
    tokenBalanceQuery,
    accountBalanceQuery,
    isHistorical
  );
};

// Get the account id condition for entity_stake query, this helps avoid a full index scan
const getEntityStakeAccountCondition = (entityAccountQuery) =>
  entityAccountQuery.query ? `and ${entityAccountQuery.query.replaceAll('e.id', 'es.id')}` : '';

const toQueryObject = (queryAndParams) => {
  return {
    query: queryAndParams[0],
    params: queryAndParams[1],
  };
};

/**
 * Gets the balance param value from the last balance query param. Defaults to true if not found.
 * @param query the http request query object
 * @return {boolean}
 */
const getBalanceParamValue = (query) => {
  const values = query[constants.filterKeys.BALANCE] || 'true';
  const lastValue = typeof values === 'string' ? values : values[values.length - 1];
  return utils.parseBooleanValue(lastValue);
};

/**
 * Handler function for /accounts API.
 *
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @return {Promise}
 */
const getAccounts = async (req, res) => {
  // Validate query parameters first
  utils.validateReq(req, acceptedAccountsParameters);

  // Parse the filter parameters for account-numbers, balances, publicKey and pagination
  const entityAccountQuery = toQueryObject(utils.parseAccountIdQueryParam(req.query, 'e.id'));
  const balanceQuery = toQueryObject(utils.parseBalanceQueryParam(req.query, 'e.balance'));
  const includeBalance = getBalanceParamValue(req.query);
  const limitAndOrderQuery = utils.parseLimitAndOrderParams(req, constants.orderFilterValues.ASC);
  const pubKeyQuery = toQueryObject(utils.parsePublicKeyQueryParam(req.query, 'public_key'));
  const tokenBalanceQuery = {query: 'account_id = e.id', params: [], limit: tokenBalanceResponseLimit.multipleAccounts};

  if (entityAccountQuery && entityAccountQuery.query) {
    tokenBalanceQuery.query += ` and ${entityAccountQuery.query.replaceAll('e.id', 'account_id')}`;
    tokenBalanceQuery.params = [...entityAccountQuery.params];
  }

  const {query, params} = getAccountQuery(
    entityAccountQuery,
    tokenBalanceQuery,
    undefined,
    balanceQuery,
    limitAndOrderQuery,
    pubKeyQuery,
    includeBalance,
    false
  );

  // Execute query
  // set random_page_cost to 0 to make the cost estimation of using the index on (public_key, index)
  // lower than that of other indexes so pg planner will choose the better index when querying by public key
  const preQueryHint = pubKeyQuery.query !== '' && constants.zeroRandomPageCostQueryHint;
  const result = await pool.queryQuietly(query, params, preQueryHint);
  const ret = {
    accounts: result.rows.map((row) => processRow(row)),
    links: {
      next: null,
    },
  };

  let anchorAcc = ret.accounts[ret.accounts.length - 1]?.account;

  ret.links = {
    next: utils.getPaginationLink(
      req,
      ret.accounts.length !== limitAndOrderQuery.limit,
      {[constants.filterKeys.ACCOUNT_ID]: anchorAcc},
      limitAndOrderQuery.order
    ),
  };

  logger.debug(`getAccounts returning ${ret.accounts.length} entries`);
  res.locals[constants.responseDataLabel] = ret;
};

/**
 * Handler function for /accounts/:idOrAliasOrEvmAddress API.
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @return {Promise}
 */
const getOneAccount = async (req, res) => {
  // Parse the filter parameters for account-numbers, balance, and pagination
  const filters = utils.buildAndValidateFilters(req.query, acceptedSingleAccountParameters);
  const encodedId = await EntityService.getEncodedId(req.params[constants.filterKeys.ID_OR_ALIAS_OR_EVM_ADDRESS]);

  const timestampFilters = [];
  let includeTransactions = true;
  for (const filter of filters) {
    switch (filter.key) {
      case filterKeys.TIMESTAMP:
        timestampFilters.push(filter);
        break;
      case filterKeys.TRANSACTIONS:
        includeTransactions = filter.value;
        break;
    }
  }
  const timestampRange = utils.parseTimestampFilters(timestampFilters, false, true, true, false, false);
  if (timestampRange.range?.isEmpty()) {
    throw new NotFoundError('Timestamp range is empty');
  }

  const accountIdParamIndex = 1;
  let paramCount = accountIdParamIndex;
  const tokenBalanceQuery = {
    query: `account_id = $${accountIdParamIndex}`,
    params: [encodedId],
    limit: tokenBalanceResponseLimit.singleAccount,
  };
  const entityAccountQuery = {query: `e.id = $${accountIdParamIndex}`, params: []};

  const accountBalanceQuery = {query: '', params: []};
  if (timestampFilters.length > 0) {
    const [balanceSnapshotTsQuery, balanceSnapshotTsParams] = utils.buildTimestampQuery(
      'consensus_timestamp',
      timestampRange,
      false
    );

    const {lower, upper} = await balances.getAccountBalanceTimestampRange(
      balanceSnapshotTsQuery.replaceAll(opsMap.eq, opsMap.lte),
      balanceSnapshotTsParams
    );

    if (upper !== undefined) {
      // Note when a balance snapshot timestamp is not found, it falls back to return balance info from entity table
      const lowerTimestampParamIndex = ++paramCount;
      const upperTimestampParamIndex = ++paramCount;
      // Note if no balance info for the specific account in the timestamp range is found, the balance should be 0.
      // It can happen when the account is just created and the very first snapshot is after the range.
      accountBalanceQuery.query = `coalesce((
        select balance
        from account_balance
        where account_id = $${accountIdParamIndex} and
          consensus_timestamp >= $${lowerTimestampParamIndex} and
          consensus_timestamp <= $${upperTimestampParamIndex}
        order by consensus_timestamp desc
        limit 1
      ), 0)`;
      accountBalanceQuery.timestampParamIndex = upperTimestampParamIndex;

      tokenBalanceQuery.params.push(lower, upper);
      tokenBalanceQuery.query += ` and consensus_timestamp >= $${lowerTimestampParamIndex} and
        consensus_timestamp <= $${upperTimestampParamIndex}`;
    } else {
      // force the query to union entity and entity history in case a valid balance snapshot is not found, so the balance
      // and its timestamp can be returned from the union
      accountBalanceQuery.forceUnionEntityHistory = true;
      tokenBalanceQuery.query = '';
    }

    const [entityTsQuery, entityTsParams] = utils.buildTimestampRangeQuery(
      Entity.getFullName(Entity.TIMESTAMP_RANGE),
      timestampRange
    );

    entityAccountQuery.query += ` and ${entityTsQuery.replaceAll('?', (_) => `$${++paramCount}`)}`;
    entityAccountQuery.params = entityAccountQuery.params.concat(entityTsParams);
  }

  const {query: entityQuery, params: entityParams} = getAccountQuery(
    entityAccountQuery,
    tokenBalanceQuery,
    accountBalanceQuery,
    undefined,
    undefined,
    undefined,
    undefined,
    timestampFilters.length > 0
  );

  const entityPromise = pool.queryQuietly(entityQuery, entityParams);

  // Add the account id path parameter as a query filter for the transactions handler
  filters.push({key: filterKeys.ACCOUNT_ID, operator: opsMap.eq, value: encodedId});
  const transactionsPromise = includeTransactions
    ? transactions.doGetTransactions(filters, req, timestampRange)
    : emptyTransactionsPromise;

  const [entityResults, transactionResults] = await Promise.all([entityPromise, transactionsPromise]);

  if (entityResults.rows.length === 0) {
    throw new NotFoundError();
  }

  if (entityResults.rows.length !== 1) {
    throw new NotFoundError('Error: Could not get entity information');
  }

  const ret = {
    ...processRow(entityResults.rows[0]),
    ...transactionResults,
  };

  logger.debug(`getOneAccount returning ${ret.transactions.length} transactions entries`);
  res.locals[constants.responseDataLabel] = ret;
};

const acceptedAccountsParameters = new Set([
  constants.filterKeys.ACCOUNT_BALANCE,
  constants.filterKeys.ACCOUNT_ID,
  constants.filterKeys.ACCOUNT_PUBLICKEY,
  constants.filterKeys.BALANCE,
  constants.filterKeys.LIMIT,
  constants.filterKeys.ORDER,
]);

const acceptedSingleAccountParameters = new Set([
  constants.filterKeys.LIMIT,
  constants.filterKeys.ORDER,
  constants.filterKeys.TIMESTAMP,
  constants.filterKeys.TRANSACTION_TYPE,
  constants.filterKeys.TRANSACTIONS,
]);

const accounts = {
  getAccounts,
  getOneAccount,
};

if (utils.isTestEnv()) {
  Object.assign(accounts, {
    getBalanceParamValue,
    processRow,
  });
}

export default accounts;
