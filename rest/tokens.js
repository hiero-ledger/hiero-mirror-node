// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';

import balances from './balances';
import {getResponseLimit} from './config';
import {
  EMPTY_STRING,
  entityTypes,
  EvmAddressType,
  filterKeys,
  httpStatusCodes,
  orderFilterValues,
  queryParamOperators,
  responseDataLabel,
  tokenTypeFilter,
} from './constants';
import EntityId from './entityId';
import {InvalidArgumentError, NotFoundError} from './errors';
import {CustomFee, Entity, Nft, NftHistory, Token, Transaction} from './model';
import {NftService, TokenService} from './service';
import * as utils from './utils';
import {CustomFeeViewModel, NftTransactionHistoryViewModel, NftViewModel} from './viewmodel';

const {default: defaultLimit} = getResponseLimit();

const customFeeSelectFields = [
  CustomFee.getFullName(CustomFee.TIMESTAMP_RANGE),
  CustomFee.getFullName(CustomFee.FIXED_FEES),
  CustomFee.getFullName(CustomFee.FRACTIONAL_FEES),
  CustomFee.getFullName(CustomFee.ROYALTY_FEES),
  CustomFee.getFullName(CustomFee.ENTITY_ID),
];

const entitySelectFields = [
  Entity.getFullName(Entity.AUTO_RENEW_ACCOUNT_ID),
  Entity.getFullName(Entity.AUTO_RENEW_PERIOD),
  Entity.getFullName(Entity.DELETED),
  Entity.getFullName(Entity.ID),
  Entity.getFullName(Entity.KEY),
  Entity.getFullName(Entity.EXPIRATION_TIMESTAMP),
  Entity.getFullName(Entity.MEMO),
  Entity.getFullName(Entity.TIMESTAMP_RANGE),
];

const tokenSelectFields = [
  Token.getFullName(Token.CREATED_TIMESTAMP),
  Token.getFullName(Token.DECIMALS),
  Token.getFullName(Token.FEE_SCHEDULE_KEY),
  Token.getFullName(Token.FREEZE_DEFAULT),
  Token.getFullName(Token.FREEZE_KEY),
  Token.getFullName(Token.FREEZE_STATUS),
  Token.getFullName(Token.INITIAL_SUPPLY),
  Token.getFullName(Token.KYC_KEY),
  Token.getFullName(Token.KYC_STATUS),
  Token.getFullName(Token.MAX_SUPPLY),
  Token.getFullName(Token.METADATA),
  Token.getFullName(Token.METADATA_KEY),
  Token.getFullName(Token.NAME),
  Token.getFullName(Token.PAUSE_KEY),
  Token.getFullName(Token.PAUSE_STATUS),
  Token.getFullName(Token.SUPPLY_KEY),
  Token.getFullName(Token.SUPPLY_TYPE),
  Token.getFullName(Token.SYMBOL),
  Token.getFullName(Token.TIMESTAMP_RANGE),
  Token.getFullName(Token.TOKEN_ID),
  Token.getFullName(Token.TOTAL_SUPPLY),
  Token.getFullName(Token.TREASURY_ACCOUNT_ID),
  Token.getFullName(Token.TYPE),
  Token.getFullName(Token.WIPE_KEY),
];

// select columns
const sqlQueryColumns = {
  DELETED: 'e.deleted',
  KEY: 'e.key',
  PUBLIC_KEY: 'e.public_key',
  SYMBOL: 't.symbol',
  TOKEN_ID: 't.token_id',
  TYPE: 't.type',
};

// query to column maps
const filterColumnMap = {
  publickey: sqlQueryColumns.PUBLIC_KEY,
  symbol: sqlQueryColumns.SYMBOL,
  [filterKeys.TOKEN_ID]: sqlQueryColumns.TOKEN_ID,
  [filterKeys.TOKEN_TYPE]: sqlQueryColumns.TYPE,
};

const nftQueryColumns = {
  ACCOUNT_ID: 'nft.account_id',
  CREATED_TIMESTAMP: 'nft.created_timestamp',
  DELETED: 'nft.deleted',
  SERIAL_NUMBER: 'nft.serial_number',
  TOKEN_ID: 'nft.token_id',
};

// query to column maps
const nftFilterColumnMap = {
  serialnumber: nftQueryColumns.SERIAL_NUMBER,
  [filterKeys.ACCOUNT_ID]: nftQueryColumns.ACCOUNT_ID,
};

const nftSelectFields = [
  'nft.account_id',
  'nft.created_timestamp',
  'nft.delegating_spender',
  'nft.deleted or coalesce(e.deleted, false) as deleted',
  'nft.metadata',
  'nft.serial_number',
  'nft.spender',
  'nft.timestamp_range',
  'nft.token_id',
];
const nftSelectQuery = ['select', nftSelectFields.join(',\n'), 'from nft'].join('\n');
const entityNftsJoinQuery = 'left join entity e on e.id = nft.token_id';

// token discovery sql queries
const tokenAccountCte = `with ta as (
  select *
  from token_account
  where account_id = $1
  order by token_id
)`;
const tokensSelectQuery = `select
    t.decimals,
    t.freeze_status,
    e.key,
    t.kyc_status,
    t.metadata,
    t.name,
    t.symbol,
    t.token_id,
    t.type
  from token t`;
const entityIdJoinQuery = 'join entity e on e.id = t.token_id';
const tokenAccountJoinQuery = 'join ta on ta.token_id = t.token_id';

// token info sql queries
const tokenInfoSelectFields = [
  ...entitySelectFields,
  ...tokenSelectFields,
  `(case when ${CustomFee.getFullName(CustomFee.TIMESTAMP_RANGE)} is not null 
     then jsonb_build_object(
      'created_timestamp', lower(${CustomFee.getFullName(CustomFee.TIMESTAMP_RANGE)}),
      'fixed_fees', ${CustomFee.getFullName(CustomFee.FIXED_FEES)},
      'fractional_fees', ${CustomFee.getFullName(CustomFee.FRACTIONAL_FEES)},
      'royalty_fees', ${CustomFee.getFullName(CustomFee.ROYALTY_FEES)},
      'token_id', ${CustomFee.getFullName(CustomFee.ENTITY_ID)})
     else null 
   end) as custom_fee`,
  `greatest(lower(${Token.getFullName(Token.TIMESTAMP_RANGE)}), 
            lower(${Entity.getFullName(Entity.TIMESTAMP_RANGE)})) as modified_timestamp`,
];
const tokenInfoOuterSelect = `select ${tokenInfoSelectFields.join(',\n')}`;
const tokenIdMatchQuery = 'where token_id = $1';

/**
 * Given top level select columns and filters from request query, extract filters and create final sql query with
 * appropriate where clauses.
 */
const extractSqlFromTokenRequest = (query, params, filters, conditions) => {
  // add filters
  let limit = defaultLimit;
  let order = orderFilterValues.ASC;
  const inClause = [];
  conditions = conditions || [];
  for (const filter of filters) {
    if (filter.key === filterKeys.LIMIT) {
      limit = filter.value;
      continue;
    }

    if (filter.key === filterKeys.NAME) {
      conditions.push(`t.name ILIKE $${params.push('%' + filter.value + '%')}`);
    }

    // handle keys that do not require formatting first
    if (filter.key === filterKeys.ORDER) {
      order = filter.value;
      continue;
    }

    // handle token type=ALL, valid param but not present in db
    if (filter.key === filterKeys.TOKEN_TYPE && filter.value === tokenTypeFilter.ALL.toUpperCase()) {
      continue;
    }

    const columnKey = filterColumnMap[filter.key];
    if (columnKey === undefined) {
      continue;
    }

    if (columnKey === sqlQueryColumns.TOKEN_ID && filter.operator === utils.opsMap.eq) {
      inClause.push(filter.value);
      continue;
    }

    conditions.push(`${filterColumnMap[filter.key]}${filter.operator}$${params.push(filter.value)}`);
  }

  if (inClause.length > 0) {
    conditions.push(`${sqlQueryColumns.TOKEN_ID} = any($${params.push(inClause)})`);
  }

  const whereQuery = conditions.length !== 0 ? `where ${conditions.join(' and ')}` : '';
  const orderQuery = `order by ${sqlQueryColumns.TOKEN_ID} ${order}`;
  const limitQuery = `limit $${params.push(limit)}`;
  query = [query, whereQuery, orderQuery, limitQuery].filter((q) => q !== '').join('\n');

  return utils.buildPgSqlObject(query, params, order, limit);
};

/**
 * Format token metadata appropriately for the REST API response.
 *
 * Tokens existing prior to 0.102 will have null metadata column values in response to the database migration
 * performed to add that column to both the token and token_history tables. For tokens created or updated since 0.102
 * that do not define metadata, the metadata column value will be an empty byte array based on the getMetadata()
 * method of the transaction body protobuf.
 *
 * In either case, when metadata is not defined for a token, the REST API response returns an empty string to
 * represent this situation. Otherwise, the base64 metadata is returned.
 *
 * @param metadata
 * @returns {string|String}
 */
const formatTokenMetadata = (metadata) => {
  return _.isNil(metadata) ? EMPTY_STRING : utils.encodeBase64(metadata);
};

/**
 * Format row in postgres query's result to object which is directly returned to user as json.
 */
const formatTokenRow = (row) => {
  return {
    admin_key: utils.encodeKey(row.key),
    metadata: formatTokenMetadata(row.metadata),
    name: row.name,
    symbol: row.symbol,
    token_id: EntityId.parse(row.token_id).toString(),
    type: row.type,
    decimals: row.decimals,
  };
};

/**
 * Creates custom fees object from an array of aggregated json objects
 *
 * @param customFee
 * @param tokenType
 * @return {{}|*}
 */
const createCustomFeeObject = (customFee, tokenType) => {
  if (!customFee) {
    return null;
  }

  const model = new CustomFee(customFee);
  const viewModel = new CustomFeeViewModel(model);
  const nonFixedFeesField = tokenType === Token.TYPES.FUNGIBLE_COMMON ? 'fractional_fees' : 'royalty_fees';
  return {
    created_timestamp: utils.nsToSecNs(viewModel.created_timestamp),
    fixed_fees: viewModel.fixed_fees,
    [nonFixedFeesField]: viewModel[nonFixedFeesField],
  };
};

const formatTokenInfoRow = (row) => {
  return {
    admin_key: utils.encodeKey(row.key),
    auto_renew_account: EntityId.parse(row.auto_renew_account_id, {isNullable: true}).toString(),
    auto_renew_period: row.auto_renew_period,
    created_timestamp: utils.nsToSecNs(row.created_timestamp),
    custom_fees: createCustomFeeObject(row.custom_fee, row.type),
    decimals: `${row.decimals}`,
    deleted: row.deleted,
    expiry_timestamp: utils.calculateExpiryTimestamp(
      row.auto_renew_period,
      row.created_timestamp,
      row.expiration_timestamp
    ),
    fee_schedule_key: utils.encodeKey(row.fee_schedule_key),
    freeze_default: row.freeze_default,
    freeze_key: utils.encodeKey(row.freeze_key),
    initial_supply: `${row.initial_supply}`,
    kyc_key: utils.encodeKey(row.kyc_key),
    max_supply: `${row.max_supply}`,
    memo: row.memo,
    metadata: formatTokenMetadata(row.metadata),
    metadata_key: utils.encodeKey(row.metadata_key),
    modified_timestamp: utils.nsToSecNs(row.modified_timestamp),
    name: row.name,
    pause_key: utils.encodeKey(row.pause_key),
    pause_status: row.pause_status,
    supply_key: utils.encodeKey(row.supply_key),
    supply_type: row.supply_type,
    symbol: row.symbol,
    token_id: EntityId.parse(row.token_id).toString(),
    total_supply: `${row.total_supply}`,
    treasury_account_id: EntityId.parse(row.treasury_account_id).toString(),
    type: row.type,
    wipe_key: utils.encodeKey(row.wipe_key),
  };
};

const validateTokenQueryFilter = (param, op, val) => {
  let ret = false;

  if (op === undefined || val === undefined) {
    return ret;
  }

  // Validate operator
  if (!utils.isValidOperatorQuery(op)) {
    return ret;
  }

  // Validate the value
  switch (param) {
    case filterKeys.ACCOUNT_ID:
      ret = EntityId.isValidEntityId(val);
      break;
    case filterKeys.ENTITY_PUBLICKEY:
      // Acceptable forms: exactly 64 characters or +12 bytes (DER encoded)
      ret = utils.isValidPublicKeyQuery(val);
      break;
    case filterKeys.LIMIT:
      ret = utils.isPositiveLong(val);
      break;
    case filterKeys.NAME:
      ret = op === queryParamOperators.eq && utils.isByteRange(val, 3, 100);
      break;
    case filterKeys.ORDER:
      // Acceptable words: asc or desc
      ret = utils.isValidValueIgnoreCase(val, Object.values(orderFilterValues));
      break;
    case filterKeys.SERIAL_NUMBER:
      ret = utils.isPositiveLong(val);
      break;
    case filterKeys.TOKEN_ID:
      ret = EntityId.isValidEntityId(val, true, EvmAddressType.NUM_ALIAS);
      break;
    case filterKeys.TOKEN_TYPE:
      ret = utils.isValidValueIgnoreCase(val, Object.values(tokenTypeFilter));
      break;
    case filterKeys.TIMESTAMP:
      ret = utils.isValidTimestampParam(val);
      break;
    default:
      // Every token parameter should be included here. Otherwise, it will not be accepted.
      break;
  }

  return ret;
};

const getTokensRequest = async (req, res) => {
  const hasNameParam = !!req.query[filterKeys.NAME];
  if (hasNameParam && req.query[filterKeys.TOKEN_ID]) {
    throw new InvalidArgumentError('token.id and name can not be used together. Use a more specific name instead.');
  }

  if (hasNameParam && req.query[filterKeys.ACCOUNT_ID]) {
    throw new InvalidArgumentError('account.id and name cannot be used together');
  }
  // validate filters, use custom check for tokens until validateAndParseFilters is optimized to handle
  // per resource unique param names
  const filters = utils.buildAndValidateFilters(req.query, acceptedTokenParameters, validateTokenQueryFilter);

  const conditions = [];
  const getTokensSqlQuery = [tokensSelectQuery];
  const getTokenSqlParams = [];

  // if account.id filter is present join on token_account and filter dissociated tokens
  const accountId = req.query[filterKeys.ACCOUNT_ID];
  if (accountId) {
    conditions.push('ta.associated is true');
    getTokensSqlQuery.unshift(tokenAccountCte);
    getTokensSqlQuery.push(tokenAccountJoinQuery);
    getTokenSqlParams.push(EntityId.parseString(accountId, {paramName: filterKeys.ACCOUNT_ID}).getEncodedId());
  }

  // add join with entities table to sql query
  getTokensSqlQuery.push(entityIdJoinQuery);

  // build final sql query
  const {query, params, order, limit} = extractSqlFromTokenRequest(
    getTokensSqlQuery.join('\n'),
    getTokenSqlParams,
    filters,
    conditions
  );

  const rows = await getTokens(query, params);
  const tokens = rows.map((r) => {
    TokenService.putTokenCache(r);
    return formatTokenRow(r);
  });

  // populate next link
  const lastTokenId = tokens.length > 0 ? tokens[tokens.length - 1].token_id : null;
  const nextLink = hasNameParam
    ? null
    : utils.getPaginationLink(
        req,
        tokens.length !== limit,
        {
          [filterKeys.TOKEN_ID]: lastTokenId,
        },
        order
      );

  res.locals[responseDataLabel] = {
    tokens,
    links: {
      next: nextLink,
    },
  };
};

const getAndValidateTokenIdRequestPathParam = (req) => {
  const options = {
    allowEvmAddress: true,
    paramName: filterKeys.TOKENID,
    evmAddressType: EvmAddressType.NUM_ALIAS,
  };
  return EntityId.parseString(req.params.tokenId, options).getEncodedId();
};

/**
 * Validates the token info timestamp filter operator.
 *
 * @param {string} op
 * @return {boolean}
 */
const isValidTokenInfoTimestampFilterOp = (op) => {
  return op === 'eq' || op === 'lt' || op === 'lte';
};

/**
 * Validates token info request filters. Only timestamp filter is supported.
 *
 * @param param
 * @param op
 * @param val
 * @return {boolean}
 */
const validateTokenInfoFilter = (param, op, val) => {
  let ret = false;

  if (op === undefined || val === undefined) {
    return ret;
  }

  if (param === filterKeys.TIMESTAMP) {
    ret = isValidTokenInfoTimestampFilterOp(op) && utils.isValidTimestampParam(val);
  }

  return ret;
};

/**
 * Transforms the timestamp filter operator: '=' maps to '<=', others stay the same
 *
 * @param {string} op
 */
const transformTimestampFilterOp = (op) => {
  const {opsMap} = utils;
  return op === opsMap.eq ? opsMap.lte : op;
};

/**
 * Gets the token info query
 *
 * @param {string} tokenId
 * @param {[]} filters
 * @return {{query: string, params: []}} the query string and params
 */
const extractSqlFromTokenInfoRequest = (tokenId, filters) => {
  const params = [tokenId];
  let tokenQuery = 'token';
  let entityQuery = 'entity';
  let customFeeQuery = 'custom_fee';

  if (filters && filters.length !== 0) {
    // honor the last timestamp filter
    const filter = filters[filters.length - 1];
    const op = transformTimestampFilterOp(filter.operator);
    const conditions = [`${CustomFee.ENTITY_ID} = $1`, `lower(${CustomFee.TIMESTAMP_RANGE}) ${op} $2`];
    params.push(filter.value);

    var conditionsSql = conditions.join(' and ');

    // include the history table in the query
    tokenQuery = buildHistoryQuery(
      tokenSelectFields,
      conditionsSql.replace('entity_id', 'token_id'),
      Token.tableName,
      Token.tableAlias
    );

    entityQuery = buildHistoryQuery(
      entitySelectFields,
      conditionsSql.replace('entity_id', 'id'),
      Entity.tableName,
      Entity.tableAlias
    );

    customFeeQuery = buildHistoryQuery(customFeeSelectFields, conditionsSql, CustomFee.tableName, CustomFee.tableAlias);
  }

  var query = `${tokenInfoOuterSelect}
            from ${tokenQuery} as ${Token.tableAlias}
            join ${entityQuery} as ${Entity.tableAlias} on ${Entity.getFullName(Entity.ID)} = ${Token.getFullName(
    Token.TOKEN_ID
  )}
            left join ${customFeeQuery} as ${CustomFee.tableAlias} on 
                 ${CustomFee.getFullName(CustomFee.ENTITY_ID)} = ${Token.getFullName(Token.TOKEN_ID)}
            ${tokenIdMatchQuery}`;

  return {
    query,
    params,
  };
};

const buildHistoryQuery = (selectColumns, conditions, tableName, tableAlias) => {
  return `
   (select ${selectColumns}
    from
    (
      (select ${selectColumns}, lower(${tableAlias}.timestamp_range) as modified_timestamp
        from ${tableName} ${tableAlias}
        where ${conditions})
      union all
      (select ${selectColumns}, lower(${tableAlias}.timestamp_range) as modified_timestamp
      from ${tableName}_history ${tableAlias}
        where ${conditions} 
        order by lower(${tableAlias}.timestamp_range) desc limit 1)
      order by modified_timestamp desc limit 1
    ) as ${tableAlias})
    `;
};

const getTokenInfoRequest = async (req, res) => {
  const tokenId = getAndValidateTokenIdRequestPathParam(req);

  // extract and validate filters from query param
  const filters = utils.buildAndValidateFilters(req.query, acceptedSingleTokenParameters, validateTokenInfoFilter);
  const {query, params} = extractSqlFromTokenInfoRequest(tokenId, filters);

  const {rows} = await pool.queryQuietly(query, params);
  if (rows.length !== 1 || !rows[0].custom_fee) {
    throw new NotFoundError();
  }

  const token = rows[0];
  if (filters.length === 0) {
    TokenService.putTokenCache(token);
  }
  res.locals[responseDataLabel] = formatTokenInfoRow(token);
};

const getTokens = async (pgSqlQuery, pgSqlParams) => {
  const {rows} = await pool.queryQuietly(pgSqlQuery, pgSqlParams);
  logger.debug(`getTokens returning ${rows.length} entries`);
  return rows;
};

// token balances select columns
const tokenBalancesSqlQueryColumns = {
  ACCOUNT_BALANCE: 'ti.balance',
  ACCOUNT_ID: 'ti.account_id',
  CONSENSUS_TIMESTAMP: 'ti.consensus_timestamp',
  ACCOUNT_PUBLICKEY: 'e.public_key',
  TOKEN_ID: 'ti.token_id',
};

/**
 * Extracts SQL query, params, order, and limit
 *
 * @param {string} tokenId encoded token ID
 * @param {[]} filters parsed and validated filters
 * @return {{query: string, limit: number, params: [], order: 'asc'|'desc'}}
 */
const extractSqlFromTokenBalancesRequest = async (tokenId, filters) => {
  const balanceConditions = [];
  const conditions = [`${tokenBalancesSqlQueryColumns.TOKEN_ID} = $1`];
  let limit = defaultLimit;
  let order = orderFilterValues.DESC;
  const params = [tokenId];
  let publicKey;
  const tsConditions = [];
  const tsParams = [];

  for (const filter of filters) {
    switch (filter.key) {
      case filterKeys.ACCOUNT_ID:
        conditions.push(`${tokenBalancesSqlQueryColumns.ACCOUNT_ID} ${filter.operator} $${params.push(filter.value)}`);
        break;
      case filterKeys.ACCOUNT_BALANCE:
        balanceConditions.push(
          `${tokenBalancesSqlQueryColumns.ACCOUNT_BALANCE} ${filter.operator} $${params.push(filter.value)}`
        );
        break;
      case filterKeys.ACCOUNT_PUBLICKEY:
        // honor the last public key filter
        publicKey = filter.value;
        break;
      case filterKeys.LIMIT:
        limit = filter.value;
        break;
      case filterKeys.ORDER:
        order = filter.value;
        break;
      case filterKeys.TIMESTAMP:
        const op = transformTimestampFilterOp(filter.operator);
        tsParams.push(filter.value);
        // balances.getAccountBalanceTimestampRange only supports MySQL style placeholders
        tsConditions.push(`${tokenBalancesSqlQueryColumns.CONSENSUS_TIMESTAMP} ${op} ?`);
        break;
      default:
        break;
    }
  }

  const joinEntityClause = publicKey
    ? `join entity as e
      on e.type = '${entityTypes.ACCOUNT}' and
        e.id = ${tokenBalancesSqlQueryColumns.ACCOUNT_ID} and
        ${tokenBalancesSqlQueryColumns.ACCOUNT_PUBLICKEY} = $${params.push(publicKey)}`
    : '';

  let query;
  if (tsConditions.length) {
    const tsQuery = tsConditions.join(' and ');
    const {lower, upper} = await balances.getAccountBalanceTimestampRange(tsQuery, tsParams);
    if (lower === undefined) {
      return {};
    }

    conditions.push(
      `${tokenBalancesSqlQueryColumns.CONSENSUS_TIMESTAMP} >= $${params.push(lower)}`,
      `${tokenBalancesSqlQueryColumns.CONSENSUS_TIMESTAMP} <= $${params.push(upper)}`
    );
    query = `select
        distinct on (ti.account_id)
        ti.account_id,
        ti.balance,
        $${params.length}::bigint as snapshot_timestamp
      from token_balance as ti
      ${joinEntityClause}
      where ${conditions.join(' and ')}
      order by ti.account_id ${order}, ti.consensus_timestamp desc`;
    if (balanceConditions.length) {
      // Apply balance filter after retrieving the latest balance as of the upper timestamp
      query = `with ti as (${query})
      select *
      from ti
      where ${balanceConditions.join(' and ')}
      order by account_id ${order}`;
    }
    query += `\nlimit $${params.push(limit)}`;
  } else {
    conditions.push('ti.associated = true');
    if (balanceConditions.length) {
      conditions.push(...balanceConditions);
    }

    query = `
      with filtered_token_accounts as (
        select ti.account_id, ti.balance, ti.balance_timestamp
          from token_account as ti
          ${joinEntityClause}
          where ${conditions.join(' and ')}
          order by ti.account_id ${order}
          limit $${params.push(limit)}
      )
      select 
        tif.account_id,
        tif.balance,
        (select MAX(balance_timestamp) from filtered_token_accounts) as consensus_timestamp
      from filtered_token_accounts as tif`;
  }

  return utils.buildPgSqlObject(query, params, order, limit);
};

const formatTokenBalanceRow = (row, decimals) => {
  return {
    account: EntityId.parse(row.account_id).toString(),
    balance: row.balance,
    decimals,
  };
};

/**
 * Handler function for /tokens/:tokenId/balances API.
 *
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 */
const getTokenBalances = async (req, res) => {
  const tokenId = getAndValidateTokenIdRequestPathParam(req);
  const filters = utils.buildAndValidateFilters(req.query, acceptedTokenBalancesParameters);

  const response = {
    timestamp: null,
    balances: [],
    links: {
      next: null,
    },
  };
  res.locals[responseDataLabel] = response;

  const {query, params, limit, order} = await extractSqlFromTokenBalancesRequest(tokenId, filters);
  if (query === undefined) {
    return;
  }

  const {rows} = await pool.queryQuietly(query, params);
  if (rows.length > 0) {
    const cachedTokens = await TokenService.getCachedTokens(new Set([tokenId]));
    const decimals = cachedTokens.get(tokenId)?.decimals ?? null;
    response.balances = rows.map((row) => formatTokenBalanceRow(row, decimals));
    const timestamp = rows[0].consensus_timestamp ?? rows[0].snapshot_timestamp;
    response.timestamp = utils.nsToSecNs(timestamp);

    const anchorAccountId = response.balances[response.balances.length - 1].account;
    response.links.next = utils.getPaginationLink(
      req,
      response.balances.length !== limit,
      {
        [filterKeys.ACCOUNT_ID]: anchorAccountId,
      },
      order
    );
  }

  logger.debug(`getTokenBalances returning ${response.balances.length} entries`);
};

/**
 * Extracts SQL query, params, order, and limit
 *
 * @param {string} tokenId encoded token ID
 * @param {string} query initial pg SQL query string
 * @param {[]} filters parsed and validated filters
 * @return {{query: string, limit: number, params: [], order: 'asc'|'desc'}}
 */
const extractSqlFromNftTokensRequest = (tokenId, query, filters) => {
  let limit = defaultLimit;
  let order = orderFilterValues.DESC;
  const conditions = [`${nftQueryColumns.TOKEN_ID} = $1`];
  const params = [tokenId];

  for (const filter of filters) {
    switch (filter.key) {
      case filterKeys.LIMIT:
        limit = filter.value;
        break;
      case filterKeys.ORDER:
        order = filter.value;
        break;
      default:
        const columnKey = nftFilterColumnMap[filter.key];
        if (!columnKey) {
          break;
        }

        conditions.push(`${columnKey} ${filter.operator} $${params.push(filter.value)}`);
        break;
    }
  }

  const whereQuery = `where ${conditions.join('\nand ')}`;
  const orderQuery = `order by ${nftQueryColumns.SERIAL_NUMBER} ${order}`;
  const limitQuery = `limit $${params.push(limit)}`;
  query = [query, entityNftsJoinQuery, whereQuery, orderQuery, limitQuery].filter((q) => q !== '').join('\n');

  return utils.buildPgSqlObject(query, params, order, limit);
};

/**
 * Extracts SQL query, params, order, and limit
 *
 * @param {string} tokenId encoded token ID
 * @param {string} serialNumber nft serial number
 * @param {string} query initial pg SQL query string
 * @return {{query: string, limit: number, params: [], order: 'asc'|'desc'}}
 */
const extractSqlFromNftTokenInfoRequest = (tokenId, serialNumber, query) => {
  // filter for token and serialNumber
  const conditions = [`${nftQueryColumns.TOKEN_ID} = $1`, `${nftQueryColumns.SERIAL_NUMBER} = $2`];
  const params = [tokenId, serialNumber];

  const whereQuery = `where ${conditions.join('\nand ')}`;
  query = [query, entityNftsJoinQuery, whereQuery].filter((q) => q !== '').join('\n');

  return utils.buildPgSqlObject(query, params, '', '');
};

/**
 * Verify serialnumber meets expected integer format and range
 */
const validateSerialNumberParam = (serialNumber) => {
  if (!utils.isPositiveLong(serialNumber)) {
    throw InvalidArgumentError.forParams(filterKeys.SERIAL_NUMBER);
  }
};

const getAndValidateSerialNumberRequestPathParam = (req) => {
  const {serialNumber} = req.params;
  validateSerialNumberParam(serialNumber);
  return utils.parseInteger(serialNumber);
};

/**
 * Handler function for /tokens/:tokenId/nfts API.
 *
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 */
const getNftTokensRequest = async (req, res) => {
  const tokenId = getAndValidateTokenIdRequestPathParam(req);
  const filters = utils.buildAndValidateFilters(req.query, acceptedNftsParameters, validateTokenQueryFilter);

  const {query, params, limit, order} = extractSqlFromNftTokensRequest(tokenId, nftSelectQuery, filters);
  const {rows} = await pool.queryQuietly(query, params);
  const response = {
    nfts: [],
    links: {
      next: null,
    },
  };

  response.nfts = rows.map((row) => {
    const nftModel = new Nft(row);
    return new NftViewModel(nftModel);
  });

  // Pagination links
  const anchorSerialNumber = response.nfts.length > 0 ? response.nfts[response.nfts.length - 1].serial_number : 0;
  response.links.next = utils.getPaginationLink(
    req,
    response.nfts.length !== limit,
    {
      [filterKeys.SERIAL_NUMBER]: anchorSerialNumber,
    },
    order
  );

  logger.debug(`getNftTokens returning ${response.nfts.length} entries`);
  res.locals[responseDataLabel] = response;
};

/**
 * Handler function for /tokens/:tokenId/nfts/:serialNumber API.
 *
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 */
const getNftTokenInfoRequest = async (req, res) => {
  utils.validateReq(req);
  const tokenId = getAndValidateTokenIdRequestPathParam(req);
  const serialNumber = getAndValidateSerialNumberRequestPathParam(req);

  const {query, params} = extractSqlFromNftTokenInfoRequest(tokenId, serialNumber, nftSelectQuery);
  const {rows} = await pool.queryQuietly(query, params);
  if (rows.length !== 1) {
    throw new NotFoundError();
  }

  logger.debug(`getNftToken info returning single entry`);
  const nftModel = new Nft(rows[0]);
  res.locals[responseDataLabel] = new NftViewModel(nftModel);
};

const nftTransactionHistorySelectFields = [
  Transaction.CONSENSUS_TIMESTAMP,
  `(select jsonb_path_query_array(
    ${Transaction.NFT_TRANSFER},
    '$[*] ? (@.token_id == $token_id && @.serial_number == $serial_number)',
    $1)
  ) as nft_transfer`,
  Transaction.NONCE,
  Transaction.PAYER_ACCOUNT_ID,
  Transaction.TYPE,
  Transaction.VALID_START_NS,
].join(',\n');

const nftTransactionHistoryDetailsQuery = `
    select ${Transaction.CONSENSUS_TIMESTAMP},
           case
               when ${Transaction.TYPE} = 35 then
                   jsonb_build_array(jsonb_build_object(
                           'is_approval', false,
                           'receiver_account_id', null,
                           'sender_account_id', null,
                           'serial_number', $2::bigint,
                           'token_id', $1::bigint))
               else
                   jsonb_path_query_array(
                           ${Transaction.NFT_TRANSFER},
                           '$[*] ? (@.token_id == $token_id && @.serial_number == $serial_number)',
                           $3)
               end as nft_transfer,
           ${Transaction.NONCE},
           ${Transaction.PAYER_ACCOUNT_ID},
           ${Transaction.TYPE},
           ${Transaction.VALID_START_NS}
    from ${Transaction.tableName}
    where ${Transaction.CONSENSUS_TIMESTAMP} = any($4)
    order by ${Transaction.CONSENSUS_TIMESTAMP}`;

/**
 * Extracts SQL query, params, order, and limit
 * The query will get all the transaction timestamps for events of an NFT including token deletion.
 *
 * @param {BigInt|Number} tokenId encoded token ID
 * @param {string} serialNumber nft serial number
 * @param {[]} filters parsed and validated filters
 * @return {{query: string, limit: number, params: [], order: 'asc'|'desc'}}
 */
const extractSqlFromNftTransferHistoryRequest = (tokenId, serialNumber, filters) => {
  let limit = defaultLimit;
  let order = orderFilterValues.DESC;

  const params = [tokenId, serialNumber];
  const nftConditions = [`${Nft.TOKEN_ID} = $1`, `${Nft.SERIAL_NUMBER} = $2`];
  const tokenDeleteConditions = [`${Entity.ID} = $1`, `${Entity.DELETED} is true`];

  // add applicable filters
  for (const filter of filters) {
    switch (filter.key) {
      case filterKeys.LIMIT:
        limit = filter.value;
        break;
      case filterKeys.ORDER:
        order = filter.value;
        break;
      case filterKeys.TIMESTAMP:
        const {operator, value} = filter;
        const paramCount = params.push(value);
        nftConditions.push(`lower(${Nft.TIMESTAMP_RANGE}) ${operator} $${paramCount}`);
        tokenDeleteConditions.push(`lower(${Entity.TIMESTAMP_RANGE}) ${operator} $${paramCount}`);
        break;
      default:
        break;
    }
  }

  const limitIndex = params.push(limit);
  const nftCondition = nftConditions.join(' and ');
  const tokenDeleteCondition = tokenDeleteConditions.join(' and ');
  const query = `select timestamp
                 from
                     ((
                     select lower(${Nft.TIMESTAMP_RANGE}) as timestamp
                     from ${Nft.tableName}
                     where ${nftCondition}
                     union all
                     (
                     select lower(${Nft.TIMESTAMP_RANGE}) as timestamp
                     from ${NftHistory.tableName}
                     where ${nftCondition}
                     order by timestamp ${order}
                     limit $${limitIndex}
                     )
                     )
                     union all
                     (
                     select lower(${Entity.TIMESTAMP_RANGE}) as timestamp
                     from ${Entity.tableName}
                     where ${tokenDeleteCondition}
                     )) as nft_event
                 order by timestamp ${order}
                     limit $${limitIndex}`;

  return utils.buildPgSqlObject(query, params, order, limit);
};

const formatNftTransactionHistoryRow = (row) => {
  const transactionModel = new Transaction(row);
  return new NftTransactionHistoryViewModel(transactionModel);
};

/**
 * Handler function for /api/v1/tokens/{tokenId}/nfts/{serialNumber}/transactions API.
 *
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 */
const getNftTransferHistoryRequest = async (req, res) => {
  const tokenId = getAndValidateTokenIdRequestPathParam(req);
  const serialNumber = getAndValidateSerialNumberRequestPathParam(req);

  const filters = utils.buildAndValidateFilters(
    req.query,
    acceptedNftTransferHistoryParameters,
    validateTokenQueryFilter
  );

  const response = {
    transactions: [],
    links: {
      next: null,
    },
  };
  res.locals[responseDataLabel] = response;

  // Get transaction consensus timestamps
  const {
    query: timestampQuery,
    params: timestampParams,
    limit,
    order,
  } = extractSqlFromNftTransferHistoryRequest(tokenId, serialNumber, filters);
  const {rows} = await pool.queryQuietly(timestampQuery, timestampParams);
  if (rows.length === 0) {
    return;
  }

  // Get nft transfer related transaction details
  const jsonbPathQueryVars = utils.JSONStringify({token_id: tokenId, serial_number: serialNumber});
  const timestamps = rows.map((r) => r.timestamp);
  const query = `${nftTransactionHistoryDetailsQuery} ${order}`;
  const params = [tokenId, serialNumber, jsonbPathQueryVars, timestamps];
  const {rows: transactions} = await pool.queryQuietly(query, params);

  response.transactions = transactions.map(formatNftTransactionHistoryRow);
  const anchorTimestamp = _.last(response.transactions)?.consensus_timestamp ?? 0;
  response.links.next = utils.getPaginationLink(
    req,
    response.transactions.length !== limit,
    {
      [filterKeys.TIMESTAMP]: anchorTimestamp,
    },
    order
  );

  if (response.transactions.length > 0) {
    // check if nft exists
    const nft = await NftService.getNft(tokenId, serialNumber);
    if (nft?.createdTimestamp == null) {
      // set 206 partial response
      res.locals.statusCode = httpStatusCodes.PARTIAL_CONTENT.code;
      logger.debug(`getNftTransferHistory returning partial content`);
    }
  }

  logger.debug(`getNftTransferHistory returning ${response.transactions.length} entries`);
};

const tokens = {
  getNftTokenInfoRequest,
  getNftTokensRequest,
  getNftTransferHistoryRequest,
  getTokenBalances,
  getTokenInfoRequest,
  getTokensRequest,
};

const acceptedNftsParameters = new Set([
  filterKeys.ACCOUNT_ID,
  filterKeys.LIMIT,
  filterKeys.ORDER,
  filterKeys.SERIAL_NUMBER,
]);

const acceptedNftTransferHistoryParameters = new Set([filterKeys.LIMIT, filterKeys.ORDER, filterKeys.TIMESTAMP]);

const acceptedTokenParameters = new Set([
  filterKeys.ACCOUNT_ID,
  filterKeys.ENTITY_PUBLICKEY,
  filterKeys.LIMIT,
  filterKeys.NAME,
  filterKeys.ORDER,
  filterKeys.TOKEN_ID,
  filterKeys.TOKEN_TYPE,
]);

const acceptedSingleTokenParameters = new Set([filterKeys.TIMESTAMP]);

const acceptedTokenBalancesParameters = new Set([
  filterKeys.ACCOUNT_ID,
  filterKeys.ACCOUNT_BALANCE,
  filterKeys.ACCOUNT_PUBLICKEY,
  filterKeys.LIMIT,
  filterKeys.ORDER,
  filterKeys.TIMESTAMP,
]);

if (utils.isTestEnv()) {
  Object.assign(tokens, {
    buildHistoryQuery,
    customFeeSelectFields,
    entityIdJoinQuery,
    entitySelectFields,
    extractSqlFromNftTokenInfoRequest,
    extractSqlFromNftTokensRequest,
    extractSqlFromNftTransferHistoryRequest,
    extractSqlFromTokenBalancesRequest,
    extractSqlFromTokenInfoRequest,
    extractSqlFromTokenRequest,
    formatNftTransactionHistoryRow,
    formatTokenBalanceRow,
    formatTokenInfoRow,
    formatTokenRow,
    nftSelectQuery,
    tokenAccountCte,
    tokenAccountJoinQuery,
    tokenInfoOuterSelect,
    tokenSelectFields,
    tokensSelectQuery,
    validateSerialNumberParam,
    validateTokenInfoFilter,
    validateTokenQueryFilter,
  });
}

export default tokens;
