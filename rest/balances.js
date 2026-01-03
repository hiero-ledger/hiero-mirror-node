// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';

import AccountAlias from './accountAlias';
import {getResponseLimit} from './config';
import * as constants from './constants';
import EntityId from './entityId';
import {EntityService} from './service';
import {EvmAddressType} from './constants';
import {InvalidArgumentError} from './errors';
import * as utils from './utils';

const {tokenBalance: tokenBalanceLimit} = getResponseLimit();

const formatBalancesResult = (req, result, limit, order) => {
  const {rows} = result;
  const ret = {
    timestamp: null,
    balances: [],
    links: {
      next: null,
    },
  };

  if (rows.length > 0) {
    const maxTimestamp = rows
      .map((r) => r.consensus_timestamp)
      .reduce((result, current) => (result > current ? result : current), 0);
    ret.timestamp = utils.nsToSecNs(maxTimestamp);
  }

  ret.balances = rows.map((row) => {
    return {
      account: EntityId.parse(row.account_id).toString(),
      balance: row.balance,
      tokens: utils.parseTokenBalances(row.token_balances),
    };
  });

  const anchorAccountId = ret.balances.length > 0 ? ret.balances[ret.balances.length - 1].account : 0;

  // Pagination links
  ret.links = {
    next: utils.getPaginationLink(
      req,
      ret.balances.length !== limit,
      {
        [constants.filterKeys.ACCOUNT_ID]: anchorAccountId,
      },
      order
    ),
  };

  return ret;
};

const entityPublicKeyQuery = `select id from entity where type in ('ACCOUNT', 'CONTRACT') and public_key = $1 limit $2`;

const getAccountIdsByPublicKey = async (publicKey, limit) => {
  if (_.isEmpty(publicKey)) {
    return null;
  }

  const params = [...publicKey, limit];
  const result = await pool.queryQuietly(entityPublicKeyQuery, params);

  if (result) {
    const ids = result.rows.map((r) => r.id);
    if (!_.isEmpty(ids)) {
      return `ab.account_id in (${ids})`;
    }
  }

  return null;
};

/**
 * Handler function for /balances API.
 * @param {Request} req HTTP request object
 * @return {Promise} Promise for PostgreSQL query
 */
const getBalances = async (req, res) => {
  utils.validateReq(req, acceptedBalancesParameters, balanceFilterValidator);

  // Parse the filter parameters for credit/debit, account-numbers, timestamp and pagination
  const [accountQuery, accountParamsPromise] = parseAccountIdQueryParam(req.query, 'ab.account_id');
  const accountParams = await Promise.all(accountParamsPromise);
  // transform the timestamp=xxxx or timestamp=eq:xxxx query in url to 'timestamp <= xxxx' SQL query condition
  let [tsQuery, tsParams] = utils.parseTimestampQueryParam(req.query, 'consensus_timestamp', {
    [utils.opsMap.eq]: utils.opsMap.lte,
  });
  const [balanceQuery, balanceParams] = utils.parseBalanceQueryParam(req.query, 'ab.balance');
  const [pubKeyQuery, pubKeyParams] = utils.parsePublicKeyQueryParam(req.query, 'public_key');
  const {
    query: limitQuery,
    params,
    order,
    limit,
  } = utils.parseLimitAndOrderParams(req, constants.orderFilterValues.DESC);

  res.locals[constants.responseDataLabel] = {
    timestamp: null,
    balances: [],
    links: {
      next: null,
    },
  };

  let sqlQuery;
  let sqlParams;

  if (tsQuery) {
    const tsQueryResult = await getTsQuery(tsQuery, tsParams);
    if (!tsQueryResult.query) {
      return;
    }

    const accountIdsQuery = await getAccountIdsByPublicKey(pubKeyParams, limit);
    if (pubKeyQuery && !accountIdsQuery) {
      return;
    }

    [sqlQuery, tsParams] = await getBalancesQuery(
      accountQuery,
      balanceQuery,
      accountIdsQuery,
      limitQuery,
      order,
      tsQueryResult
    );
    sqlParams = utils.mergeParams(tsParams, accountParams, balanceParams, params);
  } else {
    // use current balance from entity table when there's no timestamp query filter
    const conditions = [accountQuery, pubKeyQuery, balanceQuery].filter(Boolean).join(' and ');
    const whereClause = conditions && `where ${conditions}`;
    const tokenBalanceSubQuery = getTokenAccountBalanceSubQuery(order);
    sqlParams = utils.mergeParams(tsParams, accountParams, pubKeyParams, balanceParams, params);
    sqlQuery = `
      with entity_balance as (
        select id as account_id, balance, balance_timestamp as consensus_timestamp, public_key
        from entity
        where type in ('ACCOUNT', 'CONTRACT')
      )
      select ab.*, (${tokenBalanceSubQuery}) as token_balances
      from entity_balance ab
      ${whereClause}
      order by ab.account_id ${order}
      ${limitQuery}`;
  }

  const pgSqlQuery = utils.convertMySqlStyleQueryToPostgres(sqlQuery);
  const result = await pool.queryQuietly(pgSqlQuery, sqlParams);
  res.locals[constants.responseDataLabel] = formatBalancesResult(req, result, limit, order);
  logger.debug(`getBalances returning ${result.rows.length} entries`);
};

/**
 * Gets the balance snapshot timestamp range given tsQuery and tsParams. If a balance snapshot timestamp satisfying the
 * conditions is found, a range with lower bound equal to the beginning of the month of the balance snapshot timestamp,
 * and upper bound equal to the balance snapshot timestamp is returned. Otherwise, an empty object is returned.
 *
 * @param tsQuery - the timestamp query built by parsing the timestamp filter from the request url
 * @param tsParams - the timestamp parameters for the timestamp query
 * @returns {Promise<{lower: BigInt|Number, upper: BigInt|Number}|{}>}
 */
const getAccountBalanceTimestampRange = async (tsQuery, tsParams) => {
  const {lowerBound, upperBound, neParams} = getOptimizedTimestampRange(tsQuery, tsParams);
  if (lowerBound === undefined) {
    return {};
  }

  // Add the treasury account to the query as it will always be in the balance snapshot and account_id is the first
  // column of the primary key
  let condition = 'account_id = $1 and consensus_timestamp >= $2 and consensus_timestamp <= $3';
  const params = [EntityId.systemEntity.treasuryAccount.getEncodedId(), lowerBound, upperBound];
  if (neParams.length) {
    condition += ' and not consensus_timestamp = any ($4)';
    params.push(neParams);
  }

  const query = `
    select consensus_timestamp
    from account_balance
    where ${condition}
    order by consensus_timestamp desc
    limit 1`;

  const {rows} = await pool.queryQuietly(query, params);
  if (rows.length === 0) {
    return {};
  }

  const upper = rows[0].consensus_timestamp;
  const lower = utils.getFirstDayOfMonth(upper, -6);
  return {lower, upper};
};

const getBalancesQuery = async (accountQuery, balanceQuery, accountIdsQuery, limitQuery, order, tsQueryResult) => {
  const tokenBalanceSubQuery = getTokenBalanceSubQuery(order, tsQueryResult.query);
  const whereClause = `
      where ${[tsQueryResult.query, accountQuery, accountIdsQuery, balanceQuery].filter(Boolean).join(' and ')}`;
  const {lower, upper} = tsQueryResult.timestampRange;
  // The first upper is for the consensus_timestamp in the select fields, also double the lower and the upper since
  // they are used twice, in the token balance subquery and in the where clause of the main query
  const tsParams = [upper, lower, upper, lower, upper];
  const sqlQuery = `
      select distinct on (account_id)
        ab.account_id,
        ab.balance,
        ?::bigint as consensus_timestamp,
        (${tokenBalanceSubQuery}) as token_balances
      from account_balance ab
      ${whereClause}
      order by ab.account_id ${order}, ab.consensus_timestamp desc
      ${limitQuery}`;
  return [sqlQuery, tsParams];
};

/**
 * Optimize the timestamp range based on the query built from the timestamp parameters in the request URL. With the
 * assumption that the importer has synced up and the balance data close to NOW in wall clock should exist in db,
 * adjust the lower bound and upper bound timestamps so the range will cover at most two monthly time partitions.
 *
 * @param tsQuery
 * @param tsParams
 * @returns {{}|{upperBound: bigint, lowerBound: bigint, neParams: *[]}}
 */
const getOptimizedTimestampRange = (tsQuery, tsParams) => {
  let lowerBound = 0n;
  const neParams = [];
  let upperBound = constants.MAX_LONG;

  // Find the lower bound and the upper bound from tsParams if present
  tsQuery
    .split('?')
    .filter(Boolean)
    .forEach((query, index) => {
      const value = BigInt(tsParams[index]);
      // eq operator has already been converted to the lte operator
      if (query.includes(utils.opsMap.lte)) {
        // lte operator includes the lt operator, so this clause must before the lt clause
        upperBound = utils.bigIntMin(upperBound, value);
      } else if (query.includes(utils.opsMap.lt)) {
        // Convert lt to lte to simplify query
        const ltValue = value - 1n;
        upperBound = utils.bigIntMin(upperBound, ltValue);
      } else if (query.includes(utils.opsMap.gte)) {
        // gte operator includes the gt operator, so this clause must come before the gt clause
        lowerBound = utils.bigIntMax(lowerBound, value);
      } else if (query.includes(utils.opsMap.gt)) {
        // Convert gt to gte to simplify query
        const gtValue = value + 1n;
        lowerBound = utils.bigIntMax(lowerBound, gtValue);
      } else if (query.includes(utils.opsMap.ne)) {
        neParams.push(value);
      }
    });

  if (lowerBound > upperBound) {
    return {};
  }

  // The optimized range of [lower, upper] should overlap with at most two partitions, with the exception that when upper
  // is more than 6 months in the future, the range may cover more months. Since the partition maintenance job will
  // create at most one 6 month partition ahead, it's unnecessary to adjust the upper bound.
  // With the assumption that the data in db is in sync with the network, in other words, the balance information is
  // update-to-date as of NOW in wall clock, the algorithm below sets lower bound to
  //   max(lowerBound from user, first day of the month before the month min(now, upperBound) is in)
  const nowInNs = utils.nowInNs();
  const effectiveUpperBound = utils.bigIntMin(upperBound, nowInNs);
  const optimalLowerBound = utils.getFirstDayOfMonth(effectiveUpperBound, -6);
  lowerBound = utils.bigIntMax(lowerBound, optimalLowerBound);

  return {lowerBound, upperBound, neParams};
};

const getTsQuery = async (tsQuery, tsParams) => {
  const {lower, upper} = await getAccountBalanceTimestampRange(tsQuery, tsParams);
  if (lower === undefined) {
    return {};
  }

  const query = 'ab.consensus_timestamp >= ? and ab.consensus_timestamp <= ?';
  return {
    query,
    timestampRange: {
      lower,
      upper,
    },
  };
};

const getTokenBalanceSubQuery = (order, consensusTsQuery) => {
  consensusTsQuery = consensusTsQuery.replaceAll('ab.', 'tb.');
  return `
    select json_agg(json_build_object('token_id', token_id, 'balance', balance))
    from (
      select distinct on (token_id) token_id, balance
      from token_balance tb
      where tb.account_id = ab.account_id
        and ${consensusTsQuery}
      order by token_id ${order}, consensus_timestamp desc
      limit ${tokenBalanceLimit.multipleAccounts}
    ) as account_token_balance`;
};

const getTokenAccountBalanceSubQuery = (order) => {
  return `
    select json_agg(json_build_object('token_id', token_id, 'balance', balance))
    from (
      select token_id, balance
      from token_account ta
      where ta.account_id = ab.account_id and ta.associated is true
      order by token_id ${order}
      limit ${tokenBalanceLimit.multipleAccounts}
    ) as account_token_balance`;
};

const parseAccountIdQueryParam = (query, columnName) => {
  let evmAliasAddressCount = 0;
  return utils.parseParams(
    query[constants.filterKeys.ACCOUNT_ID],
    (value) => {
      if (EntityId.isValidEntityId(value, false)) {
        return EntityId.parseString(value).getEncodedId();
      }
      if (EntityId.isValidEvmAddress(value, EvmAddressType.NO_SHARD_REALM) && ++evmAliasAddressCount === 1) {
        return EntityService.getEncodedId(value, false);
      }
      if (AccountAlias.isValid(value, true) && ++evmAliasAddressCount === 1) {
        return EntityService.getAccountIdFromAlias(AccountAlias.fromString(value), false);
      }

      if (evmAliasAddressCount > 1) {
        throw new InvalidArgumentError({
          message: `Invalid parameter: ${constants.filterKeys.ACCOUNT_ID}`,
          detail: `Only one EVM address or alias is allowed.`,
        });
      }
      throw new InvalidArgumentError(`Invalid parameter: ${constants.filterKeys.ACCOUNT_ID}`);
    },
    (op, value) => {
      if (evmAliasAddressCount > 0 && op !== utils.opsMap.eq) {
        throw new InvalidArgumentError({
          message: `Invalid parameter: ${constants.filterKeys.ACCOUNT_ID}`,
          detail: `EVM address or alias only supports equals operator`,
        });
      }

      return Array.isArray(value)
        ? [`${columnName} IN (?`.concat(', ?'.repeat(value.length - 1)).concat(')'), value]
        : [`${columnName}${op}?`, [value]];
    },
    true
  );
};

const balanceFilterValidator = (param, op, val) => {
  return param === constants.filterKeys.ACCOUNT_ID
    ? utils.validateOpAndValue(op, val)
    : utils.filterValidityChecks(param, op, val);
};

const acceptedBalancesParameters = new Set([
  constants.filterKeys.ACCOUNT_BALANCE,
  constants.filterKeys.ACCOUNT_ID,
  constants.filterKeys.ACCOUNT_PUBLICKEY,
  constants.filterKeys.LIMIT,
  constants.filterKeys.ORDER,
  constants.filterKeys.TIMESTAMP,
]);

const balances = {
  getAccountBalanceTimestampRange,
  getBalances,
};

if (utils.isTestEnv()) {
  Object.assign(balances, {
    getOptimizedTimestampRange,
  });
}

export default balances;
