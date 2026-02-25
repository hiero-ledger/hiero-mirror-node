// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';
import anonymize from 'ip-anonymize';
import crypto from 'crypto';
import httpContext from 'express-http-context';
import JSONBigFactory from 'json-bigint';
import * as math from 'mathjs';
import pg from 'pg';
import pgRange, {Range} from 'pg-range';
import {format} from 'sql-formatter';
import util from 'util';

import * as constants from './constants';
import EntityId from './entityId';
import config from './config';
import ed25519 from './ed25519';
import {DbError, InvalidArgumentError, InvalidClauseError} from './errors';
import {Entity, TransactionResult, TransactionType} from './model';
import {EvmAddressType, userLimitLabel} from './constants';

const JSONBig = JSONBigFactory({useNativeBigInt: true});

const responseLimit = config.response.limit;
const resultSuccess = _.join(TransactionResult.getSuccessProtoIds(), ', ');

const opsMap = {
  lt: ' < ',
  lte: ' <= ',
  gt: ' > ',
  gte: ' >= ',
  eq: ' = ',
  ne: ' != ',
};

const gtGte = [opsMap.gt, opsMap.gte];
const ltLte = [opsMap.lt, opsMap.lte];

const gtLtPattern = /^[gl]t[e]?:/;

const emptySet = new Set();

/**
 * Returns null if the value is equal to the default, otherwise value.
 *
 * @param value
 * @param defaultValue
 * @return {*|null}
 */
const asNullIfDefault = (value, defaultValue) => {
  return value === defaultValue ? null : value;
};

/**
 * Returns the larger of the two bigint values.
 *
 * @param {BigInt} a
 * @param {BigInt} b
 * @returns {BigInt}
 */
const bigIntMax = (a, b) => (a > b ? a : b);

/**
 * Returns the smaller of the two bigint values.
 *
 * @param {BigInt} a
 * @param {BigInt} b
 * @returns {BigInt}
 */
const bigIntMin = (a, b) => (a < b ? a : b);

/**
 * Check if the given number is numeric
 * @param {String} n Number to test
 * @return {Boolean} true if n is numeric, false otherwise
 */
const isNumeric = (n) => {
  return !isNaN(parseFloat(n)) && isFinite(n);
};

// The max signed long has 19 digits
const positiveLongRegex = /^\d{1,19}$/;
const maxLong = 9223372036854775807n;

/**
 * Validates that num is a positive BigInt.
 * @param {number|string} num
 * @param {boolean} allowZero
 * @return {boolean}
 */
const isPositiveLong = (num, allowZero = false) => {
  if (!positiveLongRegex.test(num)) {
    return false;
  }

  const bigInt = BigInt(num);
  const min = allowZero ? 0 : 1;
  return bigInt >= min && bigInt <= maxLong;
};

/**
 * Strip the 0x prefix
 * @param val
 * @returns {*}
 */
const stripHexPrefix = (val) => {
  if (typeof val === 'string' && val.startsWith(constants.HEX_PREFIX)) {
    return val.substring(2);
  }

  return val;
};

/**
 * Validates that hex encoded num is a positive int.
 * @param num
 * @param allowZero
 * @returns {boolean}
 */
const isHexPositiveInt = (num, allowZero = false) => {
  if (typeof num === 'string' && num.startsWith(constants.HEX_PREFIX)) {
    num = parseInt(num, 16);
    return isPositiveLong(num, allowZero);
  }

  return false;
};

const isByteRange = (str, minSize, maxSize) => {
  const length = Buffer.from(str).length;
  return length >= minSize && length <= maxSize;
};

const nonNegativeInt32Regex = /^\d{1,10}$/;

/**
 * Validates that num is a non-negative int32.
 * @param num
 * @return {boolean}
 */
const isNonNegativeInt32 = (num) => {
  return nonNegativeInt32Regex.test(num) && Number(num) <= constants.MAX_INT32;
};

const isValidBooleanOpAndValue = (op, val) => {
  return op === constants.queryParamOperators.eq && /^(true|false)$/i.test(val);
};

const isValidTimestampParam = (timestamp) => {
  // Accepted forms: seconds or seconds.upto 9 digits
  return /^\d{1,10}$/.test(timestamp) || /^\d{1,10}\.\d{1,9}$/.test(timestamp);
};

const isValidOperatorQuery = (query) => {
  return /^(gte?|lte?|eq|ne)$/.test(query);
};

// Ed25519 has 64, ECDSA(secp256k1) has 66, and ED25519 DER encoded has 88 characters
const publicKeyPattern = /^(0x)?([0-9a-fA-F]{64}|[0-9a-fA-F]{66}|[0-9a-fA-F]{88})$/;
const isValidPublicKeyQuery = (query) => {
  return publicKeyPattern.test(query);
};

const contractTopicPattern = /^(0x)?[0-9A-Fa-f]{1,64}$/; // optional 0x followed by up to 64 hex digits
const isValidOpAndTopic = (op, query) => {
  return typeof query === 'string' && contractTopicPattern.test(query) && op === constants.queryParamOperators.eq;
};

const isValidUtf8Encoding = (query) => {
  if (!query) {
    return false;
  }
  return /^(utf-?8)$/.test(query.toLowerCase());
};

const isValidEncoding = (query) => {
  if (query === undefined) {
    return false;
  }
  query = query.toLowerCase();
  return query === constants.characterEncoding.BASE64 || isValidUtf8Encoding(query);
};

const ethHashPattern = /^(0x)?([0-9A-Fa-f]{64})$/;
const isValidEthHash = (hash) => {
  if (hash === undefined) {
    return false;
  }

  return ethHashPattern.test(hash);
};

const ethHashOrHederaHashPattern = /^(0x)?([0-9A-Fa-f]{64}|[0-9A-Fa-f]{96})$/;
const isValidEthHashOrHederaHash = (hash) => {
  if (hash === undefined) {
    return false;
  }

  return ethHashOrHederaHashPattern.test(hash);
};

const slotPattern = /^(0x)?[0-9A-Fa-f]{1,64}$/;
const isValidSlot = (slot) => slotPattern.test(slot);

const isValidValueIgnoreCase = (value, validValues) => validValues.includes(value.toLowerCase());

const lowerCaseQueryValue = (queryValue) => (typeof queryValue === 'string' ? queryValue.toLowerCase() : queryValue);

/**
 * Validate input parameters for the rest apis
 * @param {String} param Parameter to be validated
 * @param {String} opAndVal operator:value to be validated
 * @return {Boolean} true if the parameter is valid. false otherwise
 */
const paramValidityChecks = (param, opAndVal, filterValidator = filterValidityChecks) => {
  const ret = false;
  let val = null;
  let op = null;

  if (opAndVal === undefined) {
    return ret;
  }

  const splitVal = opAndVal.split(':');

  if (splitVal.length === 1) {
    op = 'eq';
    val = splitVal[0];
  } else if (splitVal.length === 2) {
    op = splitVal[0];
    val = splitVal[1];
  } else {
    return ret;
  }

  return filterValidator(param, op, val);
};

const basicOperators = Object.values(constants.queryParamOperators).filter(
  (o) => o !== constants.queryParamOperators.ne
);

/**
 * Returns false if the op or val is undefined or if the op is an invalid operator
 * @param op
 * @param val
 * @returns {boolean}
 */
const validateOpAndValue = (op, val) => {
  return !(op === undefined || val === undefined || !isValidOperatorQuery(op));
};

const filterValidityChecks = (param, op, val) => {
  if (!validateOpAndValue(op, val)) {
    return false;
  }

  let ret;
  // Validate the value
  switch (param) {
    case constants.filterKeys.ACCOUNT_BALANCE:
      ret = isPositiveLong(val, true);
      break;
    case constants.filterKeys.ACCOUNT_ID:
      ret = EntityId.isValidEntityId(val);
      break;
    case constants.filterKeys.ACCOUNT_PUBLICKEY:
      ret = isValidPublicKeyQuery(val);
      break;
    case constants.filterKeys.BALANCE:
      ret = isValidBooleanOpAndValue(op, val);
      break;
    case constants.filterKeys.BLOCK_HASH:
      ret = isValidEthHashOrHederaHash(val) && op === constants.queryParamOperators.eq;
      break;
    case constants.filterKeys.BLOCK_NUMBER:
      ret = (isPositiveLong(val, true) || isHexPositiveInt(val, true)) && _.includes(basicOperators, op);
      break;
    case constants.filterKeys.CONTRACT_ID:
      ret = isValidContractIdQueryParam(op, val);
      break;
    case constants.filterKeys.CREDIT_TYPE:
      // Acceptable words: credit or debit
      ret = isValidValueIgnoreCase(val, Object.values(constants.cryptoTransferType));
      break;
    case constants.filterKeys.ENCODING:
      // Acceptable words: binary or text
      ret = isValidEncoding(val.toLowerCase());
      break;
    case constants.filterKeys.ENTITY_PUBLICKEY:
      ret = isValidPublicKeyQuery(val);
      break;
    case constants.filterKeys.FILE_ID:
      ret = op === constants.queryParamOperators.eq && EntityId.systemEntity.isValidAddressBookFileId(val);
      break;
    case constants.filterKeys.FROM:
      ret = EntityId.isValidEntityId(val, true, constants.EvmAddressType.NO_SHARD_REALM);
      break;
    case constants.filterKeys.INDEX:
      ret = isNumeric(val) && val >= 0;
      break;
    case constants.filterKeys.INTERNAL:
      ret = isValidBooleanOpAndValue(op, val);
      break;
    case constants.filterKeys.LIMIT:
      ret = isPositiveLong(val);
      break;
    case constants.filterKeys.NODE_ID:
      ret = isPositiveLong(val, true);
      break;
    case constants.filterKeys.NONCE:
      ret = op === constants.queryParamOperators.eq && isNonNegativeInt32(val);
      break;
    case constants.filterKeys.ORDER:
      // Acceptable words: asc or desc
      ret = isValidValueIgnoreCase(val, Object.values(constants.orderFilterValues));
      break;
    case constants.filterKeys.RESULT:
      // Acceptable words: success or fail
      ret = isValidValueIgnoreCase(val, Object.values(constants.transactionResultFilter));
      break;
    case constants.filterKeys.SCHEDULED:
      ret = isValidBooleanOpAndValue(op, val);
      break;
    case constants.filterKeys.SCHEDULE_ID:
      ret = EntityId.isValidEntityId(val);
      break;
    case constants.filterKeys.SEQUENCE_NUMBER:
      ret = isPositiveLong(val);
      break;
    case constants.filterKeys.SERIAL_NUMBER:
      ret = isPositiveLong(val);
      break;
    case constants.filterKeys.SLOT:
      ret = isValidSlot(val) && _.includes(basicOperators, op);
      break;
    case constants.filterKeys.SPENDER_ID:
      ret = EntityId.isValidEntityId(val);
      break;
    case constants.filterKeys.TIMESTAMP:
      ret = isValidTimestampParam(val);
      break;
    case constants.filterKeys.TOKEN_ID:
      ret = EntityId.isValidEntityId(val, true, constants.EvmAddressType.NUM_ALIAS);
      break;
    case constants.filterKeys.TOKEN_TYPE:
      ret = isValidValueIgnoreCase(val, Object.values(constants.tokenTypeFilter));
      break;
    case constants.filterKeys.TOPIC0:
    case constants.filterKeys.TOPIC1:
    case constants.filterKeys.TOPIC2:
    case constants.filterKeys.TOPIC3:
      ret = isValidOpAndTopic(op, val);
      break;
    case constants.filterKeys.TRANSACTION_INDEX:
      ret = isPositiveLong(val, true) && op === constants.queryParamOperators.eq;
      break;
    case constants.filterKeys.TRANSACTION_TYPE:
      // Accepted forms: valid transaction type string
      ret = TransactionType.isValid(val);
      break;
    case constants.filterKeys.TRANSACTIONS:
      ret = isValidBooleanOpAndValue(op, val);
      break;
    case constants.filterKeys.TRANSACTION_HASH:
      ret = isValidEthHashOrHederaHash(val) && op === constants.queryParamOperators.eq;
      break;
    default:
      // Every parameter should be included here. Otherwise, it will not be accepted.
      ret = false;
  }

  return ret;
};

/**
 * Validates the parameter dependencies
 * @param query
 */
const filterDependencyCheck = (query) => {
  const badParams = [];
  let containsBlockNumber = false;
  let containsBlockHash = false;
  let containsTransactionIndex = false;
  for (const key of Object.keys(query)) {
    if (key === constants.filterKeys.TRANSACTION_INDEX) {
      containsTransactionIndex = true;
    } else if (key === constants.filterKeys.BLOCK_NUMBER) {
      containsBlockNumber = true;
    } else if (key === constants.filterKeys.BLOCK_HASH) {
      containsBlockHash = true;
    }
  }

  if (containsTransactionIndex && !(containsBlockNumber || containsBlockHash)) {
    badParams.push({
      key: constants.filterKeys.TRANSACTION_INDEX,
      error: 'transaction.index requires block.number or block.hash filter to be specified',
      code: 'invalidParamUsage',
    });
  }

  if (containsBlockHash && containsBlockNumber) {
    badParams.push({
      key: constants.filterKeys.BLOCK_HASH,
      error: 'cannot combine block.number and block.hash',
      code: 'invalidParamUsage',
    });
  }

  if (badParams.length) {
    throw InvalidArgumentError.forRequestValidation(badParams);
  }
};

const isValidContractIdQueryParam = (op, val) => {
  if (EntityId.isValidEvmAddress(val, constants.EvmAddressType.OPTIONAL_SHARD_REALM)) {
    return op === constants.queryParamOperators.eq;
  }
  return EntityId.isValidEntityId(val, false);
};

const isValidUserFileId = (val) => {
  return !_.isNil(val) && val !== '' && EntityId.parse(val).num > 1000;
};

/**
 * Validate input http request object
 * @param {Request} req HTTP request object
 * @param {Set} acceptedParameters List of valid parameters
 * @return {Object} result of validity check, and return http code/contents
 */
const validateReq = (req, acceptedParameters = emptySet, filterValidator) => {
  const badParams = [];
  // Check the validity of every query parameter
  for (const key in req.query) {
    if (!acceptedParameters.has(key)) {
      badParams.push({code: InvalidArgumentError.UNKNOWN_PARAM_USAGE, key});
      continue;
    }
    if (Array.isArray(req.query[key])) {
      if (!isRepeatedQueryParameterValidLength(req.query[key])) {
        badParams.push({
          code: InvalidArgumentError.PARAM_COUNT_EXCEEDS_MAX_CODE,
          key,
          count: req.query[key].length,
          max: config.query.maxRepeatedQueryParameters,
        });
        continue;
      }
      for (const val of req.query[key]) {
        if (!paramValidityChecks(key, val, filterValidator)) {
          badParams.push({code: InvalidArgumentError.INVALID_ERROR_CODE, key});
        }
      }
    } else if (!paramValidityChecks(key, req.query[key], filterValidator)) {
      badParams.push({code: InvalidArgumentError.INVALID_ERROR_CODE, key});
    }
  }

  if (badParams.length > 0) {
    throw InvalidArgumentError.forRequestValidation(badParams);
  }
};

const isRepeatedQueryParameterValidLength = (values) => values.length <= config.query.maxRepeatedQueryParameters;

const parseTimestampParam = (timestampParam) => {
  // Expect timestamp input as (a) just seconds,
  // (b) seconds.mmm (3-digit milliseconds),
  // or (c) seconds.nnnnnnnnn (9-digit nanoseconds)
  // Convert all of these formats to (seconds * 10^9 + nanoseconds) format,
  // after validating that all characters are digits
  if (!timestampParam) {
    return '';
  }
  const tsSplit = timestampParam.split('.');
  if (tsSplit.length <= 0 || tsSplit.length > 2) {
    return '';
  }
  const seconds = /^(\d)+$/.test(tsSplit[0]) ? tsSplit[0] : 0;
  let nanos = tsSplit.length === 2 && /^(\d)+$/.test(tsSplit[1]) ? tsSplit[1] : 0;
  nanos = `${nanos}000000000`.substring(0, 9);
  return `${seconds}${nanos}`;
};

/**
 * @returns {Object} null if paramValue has invalid operator, or not correctly formatted.
 */
const parseOperatorAndValueFromQueryParam = (paramValue) => {
  // Split the op:value into operation and value and create a SQL query string
  const splitItem = paramValue.split(':');
  if (splitItem.length === 1) {
    // No operator specified. Just use "eq:"
    return {op: opsMap.eq, value: splitItem[0]};
  }
  if (splitItem.length === 2) {
    if (!(splitItem[0] in opsMap)) {
      return null;
    }
    return {op: opsMap[splitItem[0]], value: splitItem[1]};
  }
  return null;
};

/**
 * Gets the effective max limit for the current request, checking httpContext
 * for authenticated user's custom limit first, falling back to default max.
 * @return {number}
 */
const getEffectiveMaxLimit = () => {
  const userLimit = httpContext.get(userLimitLabel);
  return userLimit !== undefined ? userLimit : responseLimit.max;
};

/**
 * Gets the limit param value, if not exists, return the default; otherwise cap it at max.
 * Note if values is an array, the last one is honored.
 * @param {string[]|string} values Values of the limit param
 * @return {number}
 */
const getLimitParamValue = (values) => {
  let ret = responseLimit.default;
  if (values !== undefined) {
    const value = Array.isArray(values) ? values[values.length - 1] : values;
    const parsed = Number(value);
    const maxLimit = getEffectiveMaxLimit();
    ret = parsed > maxLimit ? maxLimit : parsed;
  }
  return ret;
};

/**
 * Parse the query filter parameter
 * @param paramValues Value of the query param after parsing by ExpressJS
 * @param {Function} processValue function to extract sql params using comparator and value
 *          in the query param.
 * @param {Function} processOpAndValue function to compute partial sql clause using comparator and value
 *          in the query param.
 * @param {Boolean} allowMultiple whether the sql clause should build multiple = ops as an IN() clause
 * @return {Array} [query, params] Constructed SQL query fragment and corresponding values
 */
const parseParams = (paramValues, processValue, processQuery, allowMultiple) => {
  if (paramValues === undefined) {
    return ['', []];
  }
  // Convert paramValues to a set to remove duplicates
  if (!Array.isArray(paramValues)) {
    paramValues = new Set([paramValues]);
  } else {
    paramValues = new Set(paramValues);
  }
  const partialQueries = [];
  const values = [];
  // Iterate for each value of param. For a url '..?q=val1&q=val2', paramValues for 'q' are [val1, val2].
  const equalValues = new Set();
  for (const paramValue of paramValues) {
    const opAndValue = parseOperatorAndValueFromQueryParam(paramValue);
    if (_.isNil(opAndValue)) {
      continue;
    }
    const processedValue = processValue(opAndValue.value);
    // Equal ops have to be processed in bulk at the end to format the IN() correctly.
    if (opAndValue.op === opsMap.eq && allowMultiple) {
      equalValues.add(processedValue);
    } else {
      const queryAndValues = processQuery(opAndValue.op, processedValue);
      if (!_.isNil(queryAndValues)) {
        partialQueries.push(queryAndValues[0]);
        if (queryAndValues[1]) {
          values.push(...queryAndValues[1]);
        }
      }
    }
  }
  if (equalValues.size !== 0) {
    const queryAndValues = processQuery(opsMap.eq, Array.from(equalValues));
    partialQueries.push(queryAndValues[0]);
    values.push(...queryAndValues[1]);
  }
  const fullClause = partialQueries.join(' and ');
  validateClauseAndValues(fullClause, values);
  return [partialQueries.join(' and '), values];
};

const validateClauseAndValues = (clause, values) => {
  if ((clause.match(/\?/g) || []).length !== values.length) {
    throw new InvalidClauseError(`Invalid clause produced after parsing query parameters: number of replacement
    parameters does not equal number of values: clause: \"${clause}\", values: ${values}`);
  }
};

const parseAccountIdQueryParam = (parsedQueryParams, columnName) => {
  return parseParams(
    parsedQueryParams[constants.filterKeys.ACCOUNT_ID],
    (value) => EntityId.parseString(value).getEncodedId(),
    (op, value) => {
      return Array.isArray(value)
        ? [`${columnName} IN (?`.concat(', ?'.repeat(value.length - 1)).concat(')'), value]
        : [`${columnName}${op}?`, [value]];
    },
    true
  );
};

const parseBalanceQueryParam = (parsedQueryParams, columnName) => {
  return parseParams(
    parsedQueryParams[constants.filterKeys.ACCOUNT_BALANCE],
    (value) => value,
    (op, value) => (isNumeric(value) ? [`${columnName}${op}?`, [value]] : null),
    false
  );
};

/**
 * Parse a hex string to a buffer. Note the string optionally have a 0x prefix
 *
 * @param {string} str - The hex string
 * @returns {Buffer}
 */
const parseHexStr = (str) => Buffer.from(stripHexPrefix(str), 'hex');

/**
 * Parses the integer string into a Number if it's safe or otherwise a BigInt
 *
 * @param {string} str
 * @returns {Number|BigInt}
 */
const parseInteger = (str) => {
  const num = Number(str);
  return Number.isSafeInteger(num) ? num : BigInt(str);
};

/**
 * Parse the pagination (limit) and order parameters
 * @param {HTTPRequest} req HTTP query request object
 * @param {String} defaultOrder Order of sorting (defaults to descending)
 * @return {Object} {query, params, order} SQL query, values and order
 */
const parseLimitAndOrderParams = (req, defaultOrder = constants.orderFilterValues.DESC) => {
  // Parse the limit parameter
  const limitQuery = `${constants.filterKeys.LIMIT} ? `;
  const limitValue = getLimitParamValue(req.query[constants.filterKeys.LIMIT]);

  // Parse the order parameters (default: descending)
  let order = defaultOrder;
  const value = req.query[constants.filterKeys.ORDER];
  if (value === constants.orderFilterValues.ASC || value === constants.orderFilterValues.DESC) {
    order = value;
  }

  return buildPgSqlObject(limitQuery, [limitValue], order, limitValue);
};

const parsePublicKey = (publicKey) => {
  const publicKeyNoPrefix = publicKey ? publicKey.replace('0x', '').toLowerCase() : publicKey;
  const decodedKey = ed25519.derToEd25519(publicKeyNoPrefix);
  return decodedKey == null ? publicKeyNoPrefix : decodedKey;
};

const parsePublicKeyQueryParam = (parsedQueryParams, columnName) => {
  return parseParams(
    parsedQueryParams[constants.filterKeys.ACCOUNT_PUBLICKEY],
    (value) => {
      return parsePublicKey(value);
    },
    (op, value) => [`${columnName}${op}?`, [value]],
    false
  );
};

const parseTimestampQueryParam = (parsedQueryParams, columnName, opOverride = {}) => {
  return parseParams(
    parsedQueryParams[constants.filterKeys.TIMESTAMP],
    (value) => parseTimestampParam(value),
    (op, value) => [`${columnName}${op in opOverride ? opOverride[op] : op}?`, [value]],
    false
  );
};

/**
 * Extracts the aggregated timestamp range condition from the timestamp filters
 * @param filters
 * @param offset
 * @param timestampRangeColumn
 */
const extractTimestampRangeConditionFilters = (
  filters,
  offset = 0,
  timestampRangeColumn = Entity.getFullName(Entity.TIMESTAMP_RANGE)
) => {
  const conditions = [];
  const params = [];

  filters
    .filter((filter) => filter.key === constants.filterKeys.TIMESTAMP)
    .forEach((filter) => {
      const position = `$${params.length + offset + 1}`;
      let condition;
      let range;

      if (filter.operator === opsMap.ne) {
        // handle ne filter differently
        condition = `not ${timestampRangeColumn} @> ${position}`; // @> is the pg range "contains" operator
        range = Range(filter.value, filter.value, '[]');
      } else {
        condition = `${timestampRangeColumn} && ${position}`; // && is the pg range "overlaps" operator

        switch (filter.operator) {
          case opsMap.lt:
            range = Range(null, filter.value, '()');
            break;
          case opsMap.eq:
          case opsMap.lte:
            range = Range(null, filter.value, '(]');
            break;
          case opsMap.gt:
            range = Range(filter.value, null, '()');
            break;
          case opsMap.gte:
            range = Range(filter.value, null, '[)');
            break;
        }
      }

      conditions.push(condition);
      params.push(range);
    });

  return {
    conditions,
    params,
  };
};

const buildPgSqlObject = (query, params, order, limit) => {
  return {
    query,
    params,
    order,
    limit: Number(limit),
  };
};

const parseBooleanValue = (value) => {
  return value.toLowerCase() === 'true';
};

/**
 * Convert the positional parameters from the MySql style query (?) to Postgres style positional parameters
 * ($1, $2, etc); named parameters of the format \?([a-zA-Z][a-zA-Z0-9]*)? will get the same positional index.
 *
 * @param {String} sqlQuery MySql style query
 * @return {String} SQL query with Postgres style positional parameters
 */
const convertMySqlStyleQueryToPostgres = (sqlQuery, startIndex = 1) => {
  let paramsCount = startIndex;
  const namedParamIndex = {};
  return sqlQuery.replace(/\?([a-zA-Z][a-zA-Z0-9]*)?/g, (s) => {
    let index = namedParamIndex[s];
    if (index === undefined) {
      index = paramsCount;
      paramsCount += 1;

      if (s.length > 1) {
        namedParamIndex[s] = index;
      }
    }

    return `$${index}`;
  });
};

/**
 * Create pagination (next) link
 * @param {HTTPRequest} req HTTP query request object
 * @param {Boolean} isEnd Is the next link valid or not
 * @param {{string: {value: string, inclusive: boolean}}} lastValueMap Map of key value pairs representing last values
 *   of columns that may be filtered on
 * @param {String} order Order of sorting the results
 * @return {String} next Fully formed link to the next page
 */
const getPaginationLink = (req, isEnd, lastValueMap, order) => {
  if (isEnd) {
    return null;
  }

  const urlPrefix = config.response.includeHostInLink ? `${req.protocol}://${req.hostname}:${config.port}` : '';
  const nextParamQueries = getNextParamQueries(order, req.query, lastValueMap);
  if (nextParamQueries === null) {
    return null;
  }

  // remove the '/' at the end of req.path
  const path = req.path.endsWith('/') ? req.path.slice(0, -1) : req.path;
  return urlPrefix + req.baseUrl + path + nextParamQueries;
};

/**
 * Construct the query string from the query object
 * @param {Object} reqQuery request query object
 * @returns url string
 */
const constructStringFromUrlQuery = (reqQuery) => {
  let next = '';
  for (const [q, v] of Object.entries(reqQuery)) {
    if (Array.isArray(v)) {
      v.forEach((vv) => (next += `${(next === '' ? '?' : '&') + q}=${vv}`));
    } else {
      next += `${(next === '' ? '?' : '&') + q}=${v}`;
    }
  }

  return next;
};

/**
 * Go through the query parameters, and if there is a 'field=gt:xxxx' (asc order)
 * or 'field=lt:xxxx' (desc order) fields, then remove that, to be replaced by the new continuation value
 */
const updateReqQuery = (reqQuery, field, pattern, insertValue) => {
  const fieldValues = reqQuery[field];
  if (Array.isArray(fieldValues)) {
    reqQuery[field] = fieldValues.filter((value) => !pattern.test(value));
  } else if (pattern.test(fieldValues)) {
    delete reqQuery[field];
  }

  if (field in reqQuery) {
    if (gtLtPattern.test(fieldValues)) {
      reqQuery[field] = [].concat(reqQuery[field]).concat(insertValue);
    }
  } else {
    reqQuery[field] = insertValue;
  }
};

const operatorPatterns = {
  [constants.orderFilterValues.ASC]: /gt[e]?:/,
  [constants.orderFilterValues.DESC]: /lt[e]?:/,
};

const getNextParamQueries = (order, reqQuery, lastValueMap) => {
  const pattern = operatorPatterns[order];
  const newPattern = order === constants.orderFilterValues.ASC ? 'gt' : 'lt';

  let firstField = null;
  let primaryField = null;
  for (const [field, lastValue] of Object.entries(lastValueMap)) {
    let value = lastValue;
    let inclusive = false;
    if (typeof value === 'object' && 'value' in lastValue) {
      value = lastValue.value;
      inclusive = lastValue.inclusive;
    }
    const insertValue = inclusive ? `${newPattern}e:${value}` : `${newPattern}:${value}`;
    updateReqQuery(reqQuery, field, pattern, insertValue);

    firstField = firstField ?? field;
    if (lastValue.primary) {
      primaryField = field;
    }
  }

  primaryField = primaryField ?? firstField;
  if (isEmptyRange(primaryField, reqQuery[primaryField])) {
    return null;
  }

  return constructStringFromUrlQuery(reqQuery);
};

/**
 * Given the query key, and its values, check if it results in an empty range.
 * @param key
 * @param value
 * @returns {boolean}
 */
const isEmptyRange = (key, value) => {
  const values = Array.isArray(value) ? value : [value];
  let lower = null;
  let upper = null;

  for (const v of values) {
    if (!gtLtPattern.test(v)) {
      continue;
    }

    const filter = buildComparatorFilter(key, v);
    formatComparator(filter);

    // formatComparator doesn't handle CONTRACT_ID and SLOT
    if (key === constants.filterKeys.CONTRACT_ID) {
      filter.value = EntityId.parse(filter.value).getEncodedId();
    } else if (key === constants.filterKeys.SLOT) {
      filter.value = addHexPrefix(filter.value);
    }

    if (filter.value == null) {
      continue;
    }

    let parsed = BigInt(filter.value);
    switch (filter.operator) {
      case opsMap.gt:
        parsed += 1n;
      case opsMap.gte:
        lower = lower === null ? parsed : bigIntMax(lower, parsed);
        break;
      case opsMap.lt:
        parsed -= 1n;
      case opsMap.lte:
        upper = upper === null ? parsed : bigIntMin(upper, parsed);
        break;
      default:
        break;
    }
  }

  if (lower === null || upper === null) {
    return false;
  }

  return upper < lower;
};

/**
 * Merges params arrays. Pass [] as initial if the params arrays should stay unmodified. Note every params should be
 * an array.
 * @param {any[]} initial
 * @param {any[]} params
 */
const mergeParams = (initial, ...params) => {
  return params.reduce((previous, current) => {
    previous.push(...current);
    return previous;
  }, initial);
};

const nowInNs = () => BigInt(Date.now()) * constants.NANOSECONDS_PER_MILLISECOND;

/**
 * Converts nanoseconds since epoch to seconds.nnnnnnnnn format
 *
 * @param {BigInt|Number|String} ns Nanoseconds since epoch
 * @param {String} sep separator between seconds and nanos, default is '.'
 * @return {String} Seconds since epoch (seconds.nnnnnnnnn format)
 */
const nsToSecNs = (ns, sep = '.') => {
  if (_.isNil(ns)) {
    return null;
  }

  ns = `${ns}`;
  const secs = ns.substring(0, ns.length - 9).padStart(1, '0');
  const nanos = ns.slice(-9).padStart(9, '0');
  return `${secs}${sep}${nanos}`;
};

/**
 * Converts nanoseconds since epoch to seconds-nnnnnnnnn format
 * @param {String} ns Nanoseconds since epoch
 * @return {String} Seconds since epoch (seconds-nnnnnnnnn format)
 */
const nsToSecNsWithHyphen = (ns) => {
  return nsToSecNs(ns, '-');
};

/**
 * Given a timestamp, returns the timestamp of the first day of that month
 * @param {BigInt} secNs nanoseconds since epoch
 * @param {Number} delta number of delta months
 * @returns {BigInt} ns at first day of month
 */
const getFirstDayOfMonth = (secNs, delta = 0) => {
  if (typeof secNs !== 'bigint') {
    secNs = BigInt(secNs);
  }

  // Convert nanoseconds to milliseconds for Date object
  const timestampMs = secNs / constants.NANOSECONDS_PER_MILLISECOND;
  const timestampDate = new Date(Number(timestampMs));
  const firstDayMs = Date.UTC(timestampDate.getUTCFullYear(), timestampDate.getUTCMonth() + delta);
  // Convert milliseconds to nanoseconds
  return BigInt(firstDayMs) * constants.NANOSECONDS_PER_MILLISECOND;
};

/**
 * Increment timestamp (nnnnnnnnnnnnnnnnnnn format) by 1 day
 * @return {String} (seconds.nnnnnnnnn format)
 */
const incrementTimestampByOneDay = (ns) => {
  if (_.isNil(ns)) {
    return null;
  }

  const result = BigInt(ns) + constants.ONE_DAY_IN_NS;
  return nsToSecNs(result);
};

const randomBytesAsync = util.promisify(crypto.randomBytes);

const randomString = async (length) => {
  const bytes = await randomBytesAsync(Math.max(2, length) / 2);
  return bytes.toString('hex');
};

const addHexPrefix = (hexData) => {
  if (_.isEmpty(hexData)) {
    return constants.HEX_PREFIX;
  }

  const hexString = typeof hexData === 'string' ? hexData : Buffer.from(hexData).toString();
  return hexString.substring(0, 2) === constants.HEX_PREFIX ? hexString : `${constants.HEX_PREFIX}${hexString}`;
};

/**
 * Pads all non-null arrays to 0x-prefixed 64 characters hex string and pass the null values as null
 * @param val
 * @returns {String|null}
 */
const toUint256 = (val) => {
  if (_.isNil(val)) {
    return null;
  }

  if (!val.length) {
    return constants.ZERO_UINT256;
  }

  return toHexString(val, true, 64);
};

const hexStrPattern = /^([0-9a-fA-F]{2})+$/;

/**
 * Converts a value into hex string, the value can be a number, a bigint, a hex string, an array of numbers, or a Buffer
 * Logic conforms with ETH hex value encoding, therefore nill and empty return '0x'
 * @param {Object} value Value to be converted to hex string
 * @param {boolean} addPrefix Whether to add the '0x' prefix to the hex string
 * @param {Number} padLength The length to left pad the result hex string
 * @return {String} Converted hex string
 */
const toHexString = (value, addPrefix = false, padLength = undefined) => {
  let encoded;
  if (Number.isInteger(value) || typeof value === 'bigint') {
    encoded = value.toString(16);
  } else if (typeof value === 'string' && hexStrPattern.test(value)) {
    encoded = value;
  } else if (_.isEmpty(value)) {
    return constants.HEX_PREFIX;
  } else if (Array.isArray(value)) {
    encoded = Buffer.from(value).toString('hex');
  } else if (Buffer.isBuffer(value)) {
    encoded = value.toString('hex');
  } else {
    return constants.HEX_PREFIX;
  }

  const modifiers = [];
  if (padLength !== undefined) {
    modifiers.push((s) => s.padStart(padLength, '0'));
  }

  if (addPrefix) {
    modifiers.push(addHexPrefix);
  }

  return modifiers.reduce((v, f) => f(v), encoded);
};

const toHexStringQuantity = (byteArray) => {
  let hex = toHexString(byteArray, true);
  if (hex.length > 3) {
    hex = hex.replace('0x0', '0x');
  }

  return hex;
};

const toHexStringNonQuantity = (byteArray) => {
  return toHexString(byteArray, true, 2);
};

// These match protobuf encoded hex strings. The prefixes listed check if it's a primitive key, a key list with one
// primitive key, or a 1/1 threshold key, respectively.
const PATTERN_ECDSA = /^(3a21|32250a233a21|2a29080112250a233a21)([A-Fa-f0-9]{66})$/;
const PATTERN_ED25519 = /^(1220|32240a221220|2a28080112240a221220)([A-Fa-f0-9]{64})$/;

// An empty or default key list appears as the sentinel value /x3200
const IMMUTABLE_SENTINEL_KEY = '3200';

/**
 * Converts a key for returning in JSON output
 * @param {Array} key Byte array representing the key
 * @return {Object} Key object - with type decoration for primitive keys, if detected
 */
const encodeKey = (key) => {
  if (_.isNil(key)) {
    return null;
  }

  // check for empty case to support differentiation between empty and null keys
  const keyHex = _.isEmpty(key) ? '' : toHexString(key);
  if (keyHex === IMMUTABLE_SENTINEL_KEY) {
    return null;
  }

  const ed25519Key = keyHex.match(PATTERN_ED25519);
  if (ed25519Key) {
    return {
      _type: constants.keyTypes.ED25519,
      key: ed25519Key[2],
    };
  }

  const ecdsa = keyHex.match(PATTERN_ECDSA);
  if (ecdsa) {
    return {
      _type: constants.keyTypes.ECDSA_SECP256K1,
      key: ecdsa[2],
    };
  }

  return {
    _type: constants.keyTypes.PROTOBUF,
    key: keyHex,
  };
};

/**
 * Base64 encoding of a byte array for returning in JSON output
 * @param {Array} key Byte array to be encoded
 * @return {String} base64 encoded string
 */
const encodeBase64 = (buffer) => {
  return encodeBinary(buffer, constants.characterEncoding.BASE64);
};

/**
 * Base64 encoding of a byte array for returning in JSON output
 * @param {Array} key Byte array to be encoded
 * @return {String} utf-8 encoded string
 */
const encodeUtf8 = (buffer) => {
  return encodeBinary(buffer, constants.characterEncoding.UTF8);
};

const encodeBinary = (buffer, encoding) => {
  // default to base64 encoding
  let charEncoding = constants.characterEncoding.BASE64;
  if (isValidUtf8Encoding(encoding)) {
    charEncoding = constants.characterEncoding.UTF8;
  }

  return _.isNil(buffer) ? null : buffer.toString(charEncoding);
};

/**
 *
 * @param {String} num Nullable number
 * @returns {Any} representation of math.bignumber value of parameter or null if null
 */
const getNullableNumber = (num) => {
  return _.isNil(num) ? null : `${num}`;
};

/**
 * @returns {String} transactionId of format shard.realm.num-sssssssssss-nnnnnnnnn
 */
const createTransactionId = (entityStr, validStartTimestamp) => {
  return `${entityStr}-${nsToSecNsWithHyphen(validStartTimestamp)}`;
};

/**
 * Builds the filters from HTTP request query, validates and parses the filters.
 *
 * @param query
 * @param {Set} acceptedParameters
 * @param {function(string, string, string)} filterValidator
 * @param {function(array)} filterDependencyChecker
 * @return {[]}
 */
const buildAndValidateFilters = (
  query,
  acceptedParameters,
  filterValidator = filterValidityChecks,
  filterDependencyChecker = filterDependencyCheck
) => {
  const {badParams, filters} = buildFilters(query);
  const {invalidParams, unknownParams} = validateAndParseFilters(filters, filterValidator, acceptedParameters);
  badParams.push(...invalidParams);
  badParams.push(...unknownParams);
  if (badParams.length > 0) {
    throw InvalidArgumentError.forRequestValidation(badParams);
  }

  if (filterDependencyChecker) {
    filterDependencyChecker(query);
  }

  return filters;
};

/**
 * Build the filters from the HTTP request query
 *
 * @param query
 */
const buildFilters = (query) => {
  const badParams = [];
  const filters = [];

  for (const [key, values] of Object.entries(query)) {
    // for repeated params val will be an array
    if (Array.isArray(values)) {
      if (!isRepeatedQueryParameterValidLength(values)) {
        badParams.push({
          code: InvalidArgumentError.PARAM_COUNT_EXCEEDS_MAX_CODE,
          key,
          count: values.length,
          max: config.query.maxRepeatedQueryParameters,
        });
        continue;
      }

      for (const val of values) {
        filters.push(buildComparatorFilter(key, val));
      }
    } else {
      filters.push(buildComparatorFilter(key, values));
    }
  }

  return {badParams, filters};
};

const buildComparatorFilter = (name, filter) => {
  const splitVal = filter.split(':');
  const value = splitVal.pop();
  const operator = splitVal.pop() ?? 'eq';

  return {
    key: name,
    operator,
    value,
  };
};

/**
 * Calculates the expiryTimestamp.
 * @param {int} autoRenewPeriod: seconds format
 * @param {BigInt} createdTimestamp: nnnnnnnnnnnnnnnnnnn format
 * @param {BigInt} expirationTimestamp: nnnnnnnnnnnnnnnnnnn format
 * @returns {BigInt} nnnnnnnnnnnnnnnnnnn format
 */
const calculateExpiryTimestamp = (autoRenewPeriod, createdTimestamp, expirationTimestamp) => {
  return _.isNil(expirationTimestamp) && !_.isNil(createdTimestamp) && !_.isNil(autoRenewPeriod)
    ? BigInt(createdTimestamp) + BigInt(autoRenewPeriod) * constants.AUTO_RENEW_PERIOD_MULTIPLE
    : expirationTimestamp;
};

/**
 * Verify param and filters meet expected format
 *
 * @param filters
 * @param filterValidator
 * @param {Set} acceptedParameters
 * @returns {string[], []{}} bad parameters, unknown parameters
 */
const validateFilters = (filters, filterValidator, acceptedParameters) => {
  const invalidParams = [];
  const unknownParams = [];
  for (const filter of filters) {
    if (!acceptedParameters.has(filter.key)) {
      unknownParams.push({key: filter.key, code: InvalidArgumentError.UNKNOWN_PARAM_USAGE});
      continue;
    }
    if (!filterValidator(filter.key, filter.operator, filter.value)) {
      invalidParams.push(filter.key);
    }
  }

  return {invalidParams, unknownParams};
};

/**
 * Update format to be persistence query compatible
 * @param filters
 */
const formatFilters = (filters) => {
  for (const filter of filters) {
    formatComparator(filter);
  }
};

const zeroPaddingRegex = /^0+/;

/**
 * Update slot format to be persistence query compatible
 * @param slot
 * @param leftPad
 */
const formatSlot = (slot, leftPad = false) => {
  if (leftPad) {
    return Buffer.from(stripHexPrefix(slot).padStart(64, 0), 'hex');
  }

  let formattedSlot = stripHexPrefix(slot).replace(zeroPaddingRegex, '');
  if (formattedSlot.length % 2) {
    // An odd length will result in truncation when passed to Buffer, so pad to an even value
    formattedSlot = '0' + formattedSlot;
  }
  return Buffer.from(formattedSlot, 'hex');
};

/**
 * Verify param and filters meet expected format
 * Additionally update format to be persistence query compatible
 *
 * @param filters
 * @param filterValidator
 * @param {Set} acceptedParameters
 * @returns {string[], []{}} bad parameters, unknown parameters
 */
const validateAndParseFilters = (filters, filterValidator, acceptedParameters) => {
  const {invalidParams, unknownParams} = validateFilters(filters, filterValidator, acceptedParameters);
  if (invalidParams.length === 0 && unknownParams.length === 0) {
    formatFilters(filters);
  }
  return {invalidParams, unknownParams};
};

const formatComparator = (comparator) => {
  if (comparator.operator in opsMap) {
    // update operator
    comparator.operator = opsMap[comparator.operator];

    // format value
    switch (comparator.key) {
      case constants.filterKeys.ACCOUNT_ID:
        // Accepted forms: shard.realm.num or encoded ID string
        comparator.value = EntityId.parseString(comparator.value).getEncodedId();
        break;
      case constants.filterKeys.ACCOUNT_PUBLICKEY:
        comparator.value = parsePublicKey(comparator.value);
        break;
      case constants.filterKeys.BLOCK_HASH:
        comparator.value = stripHexPrefix(comparator.value);
        break;
      case constants.filterKeys.BLOCK_NUMBER:
        if (comparator.value.startsWith(constants.HEX_PREFIX)) {
          comparator.value = parseInt(comparator.value, 16);
        }
        break;
      case constants.filterKeys.FILE_ID:
        // Accepted forms: shard.realm.num or encoded ID string
        comparator.value = EntityId.parseString(comparator.value).getEncodedId();
        break;
      case constants.filterKeys.ENTITY_PUBLICKEY:
        comparator.value = parsePublicKey(comparator.value);
        break;
      case constants.filterKeys.FROM:
        if (!EntityId.isValidEvmAddress(comparator.value)) {
          // Only parse non-evm addresses.
          comparator.value = EntityId.parseString(comparator.value, {
            evmAddressType: constants.EvmAddressType.NO_SHARD_REALM,
            paramName: comparator.key,
          }).getEncodedId();
        }
        break;
      case constants.filterKeys.INTERNAL:
        comparator.value = parseBooleanValue(comparator.value);
        break;
      case constants.filterKeys.LIMIT:
        comparator.value = math.min(Number(comparator.value), getEffectiveMaxLimit());
        break;
      case constants.filterKeys.NODE_ID:
      case constants.filterKeys.NONCE:
        comparator.value = Number(comparator.value);
        break;
      case constants.filterKeys.SCHEDULED:
        comparator.value = parseBooleanValue(comparator.value);
        break;
      case constants.filterKeys.SCHEDULE_ID:
        // Accepted forms: shard.realm.num or num
        comparator.value = EntityId.parseString(comparator.value).getEncodedId();
        break;
      case constants.filterKeys.SPENDER_ID:
        // Accepted forms: shard.realm.num or num
        comparator.value = EntityId.parseString(comparator.value).getEncodedId();
        break;
      case constants.filterKeys.TIMESTAMP:
        comparator.value = parseTimestampParam(comparator.value);
        break;
      case constants.filterKeys.TOKEN_ID:
        // Accepted forms: shard.realm.num or num
        comparator.value = EntityId.parseString(comparator.value, {
          allowEvmAddress: true,
          evmAddressType: EvmAddressType.NUM_ALIAS,
        }).getEncodedId();
        break;
      case constants.filterKeys.TOKEN_TYPE:
        // db requires upper case matching for enum
        // 'type' is overloaded for both transaction credit / debit type and token type, as a result, the transactions
        // endpoint need to handle the value in all upper cases
        comparator.value = comparator.value.toUpperCase();
        break;
      case constants.filterKeys.TRANSACTIONS:
        comparator.value = parseBooleanValue(comparator.value);
        break;
      // case 'type':
      //   // Acceptable words: credit or debit
      //   comparator.value = ;
      //   break;
      // case 'result':
      //   // Acceptable words: success or fail
      //   comparator.value = ;
      //   break;
      default:
    }
  }
};

/**
 * Parses tokenBalances into an array of {token_id: string, balance: Number} objects
 *
 * @param {{token_id: string, balance: Number}[]} tokenBalances array of token balance objects
 * @return {[]|{token_id: string, balance: Number}[]}
 */
const parseTokenBalances = (tokenBalances) => {
  if (_.isNil(tokenBalances)) {
    return [];
  }

  return tokenBalances
    .filter((x) => !_.isNil(x.token_id))
    .map((tokenBalance) => {
      const {token_id: tokenId, balance} = tokenBalance;
      return {
        token_id: EntityId.parse(tokenId).toString(),
        balance,
      };
    });
};

const isTestEnv = () => process.env.NODE_ENV === 'test';

/**
 * Masks the given IP based on Google Analytics standards
 * @param {String} ip the IP address from the req object.
 * @returns {String} The masked IP address
 */
const ipMask = (ip) => {
  return anonymize(ip, 24, 48);
};

const recordQuery = (() => {
  let func;
  return async (callerInfo, query) => {
    if (!func) {
      func = (await import('./__tests__/tableUsage')).recordQuery;
    }

    func(callerInfo, query);
  };
})();

/**
 * Gets the pool class with queryQuietly
 */
const getPoolClass = () => {
  const {Pool} = pg;
  Pool.prototype.queryQuietly = async function (query, params = [], preQueryHint = undefined) {
    let client;
    let result;
    let startTime;

    params = Array.isArray(params) ? params : [params];
    const clientErrorCallback = (error) => {
      logger.error(`error event emitted on pg pool. ${error.stack}`);
    };

    const traceEnabled = logger.isTraceEnabled();
    if (traceEnabled || isTestEnv()) {
      const callerInfo = new Error().stack
        .split('\n')
        .splice(1)
        .filter((s) => !(s.includes('utils.js') || s.includes('baseService.js')))
        .map((entry) => {
          const result = entry.match(/^\s*at\s+(\S+)[^(]+\((.*\/(.*\.js)):(\d+):.*\)$/);
          return result?.length === 5 && {function: result[1], file: result[3], line: result[4], path: result[2]};
        })[0];

      if (isTestEnv()) {
        await recordQuery(callerInfo, query);
      }

      if (traceEnabled) {
        startTime = Date.now();
        const prettyQuery = format(query, {language: 'postgresql'});
        logger.trace(
          `${callerInfo.function} (${callerInfo.file}:${
            callerInfo.line
          })\nquery: ${prettyQuery}\nparams: ${JSONStringify(params)}`
        );
      }
    }

    try {
      if (!preQueryHint) {
        result = await this.query(query, params);
      } else {
        client = await this.connect();
        client.on('error', clientErrorCallback);
        await client.query(`begin; ${preQueryHint}`);
        result = await client.query(query, params);
        await client.query('commit');
      }

      if (traceEnabled) {
        const elapsed = Date.now() - startTime;
        logger.trace(`Query took ${elapsed} ms and returned ${result.rows.length} entries`);
      }

      return result;
    } catch (err) {
      if (client !== undefined) {
        await client.query('rollback');
      }
      throw new DbError(err.message);
    } finally {
      if (client !== undefined) {
        client.off('error', clientErrorCallback);
        client.release();
      }
    }
  };

  return Pool;
};

/**
 * Gets the staking period view model object for the given staking period timestamp.
 *
 * @param {number} stakingPeriod
 * @returns {Object|null}
 */
const getStakingPeriod = (stakingPeriod) => {
  if (_.isNil(stakingPeriod)) {
    return null;
  } else {
    const stakingPeriodStart = BigInt(stakingPeriod) + 1n;
    return {
      from: nsToSecNs(stakingPeriodStart),
      to: incrementTimestampByOneDay(stakingPeriodStart),
    };
  }
};

/**
 * Checks that the timestamp filters contains either a valid range (greater than and less than filters that do not span
 * beyond a configured limit), or a set of equals operators within the same limit. When there are multiple gt / gte
 * filters, the largest value is returned as the lower bound. Same applies to lt / lte filters so the smallest values is
 * returned as the upper bound. For equals and not equals operators, the values are unique.
 *
 * @param {[]}filters an array of timestamp filters
 * @param filterRequired indicates whether timestamp filters can be empty
 * @param allowNe indicates whether existence of ne filter is error condition
 * @param allowOpenRange indicates if we allow lt or gt to be passed individually
 * @param strictCheckOverride override feature flag to force strict checking
 * @param validateRange indicates whether to validate the range is not empty and within the configured limit
 * @return range is null in the case of equals
 */
const parseTimestampFilters = (
  filters,
  filterRequired = true,
  allowNe = false,
  allowOpenRange = false,
  strictCheckOverride = true,
  validateRange = true
) => {
  const forceStrictChecks = strictCheckOverride || config.strictTimestampParam;

  if (filters.length === 0) {
    if (filterRequired) {
      throw new InvalidArgumentError('No timestamp range or eq operator provided');
    }

    return {range: null, neValues: [], eqValues: []};
  }

  let earliest = null;
  let latest = null;
  let range = null;
  const eqValues = new Set();
  const neValues = new Set();
  let lowerBoundFilterCount = 0;
  let upperBoundFilterCount = 0;

  for (const filter of filters) {
    let value = BigInt(filter.value);
    switch (filter.operator) {
      case opsMap.eq:
        eqValues.add(value);
        break;
      case opsMap.ne:
        neValues.add(value);
        break;
      case opsMap.gt:
        value += 1n;
      case opsMap.gte:
        earliest = bigIntMax(earliest ?? 0n, value);
        lowerBoundFilterCount += 1;
        break;
      case opsMap.lt:
        value -= 1n;
      case opsMap.lte:
        latest = bigIntMin(latest ?? constants.MAX_LONG, value);
        upperBoundFilterCount += 1;
        break;
    }
  }

  if (forceStrictChecks) {
    if (!allowNe && neValues.size > 0) {
      throw new InvalidArgumentError('Not equals operator not supported for timestamp param');
    }

    if (lowerBoundFilterCount > 1) {
      throw new InvalidArgumentError('Multiple gt or gte operators not permitted for timestamp param');
    }

    if (upperBoundFilterCount > 1) {
      throw new InvalidArgumentError('Multiple lt or lte operators not permitted for timestamp param');
    }

    if (eqValues.size > 0 && (lowerBoundFilterCount > 0 || upperBoundFilterCount > 0 || neValues.size > 0)) {
      throw new InvalidArgumentError('Cannot combine eq with ne, gt, gte, lt, or lte for timestamp param');
    }
  }

  if (!allowOpenRange && eqValues.size === 0 && (lowerBoundFilterCount === 0 || upperBoundFilterCount === 0)) {
    throw new InvalidArgumentError('Timestamp range must have gt (or gte) and lt (or lte), or eq operator');
  }

  const difference = latest !== null && earliest !== null ? latest - earliest + 1n : null;

  if (validateRange) {
    const {maxTimestampRange, maxTimestampRangeNs} = config.query;

    // If difference is null, we want to ignore because we allow open ranges and that is known to be true at this point
    if (difference !== null && (difference > maxTimestampRangeNs || difference <= 0n)) {
      throw new InvalidArgumentError(
        `Timestamp range by the lower and upper bounds must be positive and within ${maxTimestampRange}`
      );
    }
  }

  if (earliest !== null || latest !== null) {
    // Return an empty range when the latest is less than the earliest. Note Range(1, 0, '[]').isEmpty() is false, so
    // need to return Range() instead.
    range = difference !== null && difference <= 0n ? Range() : Range(earliest, latest, '[]');
  }

  // Note that we don't further check if the range and the eq values can result in an empty range, because some
  // endpoints treat eq operator as lte
  return {range, eqValues: Array.from(eqValues), neValues: Array.from(neValues)};
};

/**
 * Intended to be used when it is possible for different API routes to have conflicting paths
 * and only one of them needs to be executed. E.g:
 * /contracts/results
 * /contracts/:contractId
 *
 * @param req
 * @param paramName
 * @param possibleConflicts
 * @returns {boolean}
 */
const conflictingPathParam = (req, paramName, possibleConflicts = []) => {
  if (!Array.isArray(possibleConflicts)) {
    possibleConflicts = [possibleConflicts];
  }
  return req.params[paramName] && possibleConflicts.indexOf(req.params[paramName]) !== -1;
};

(function () {
  // config pg bigint parsing
  const pgTypes = pg.types;
  pgTypes.setTypeParser(20, parseInteger); // int8
  const parseBigIntArray = pgTypes.getTypeParser(1016); // int8[]
  pgTypes.setTypeParser(1016, (a) => parseBigIntArray(a).map(parseInteger));

  pgTypes.setTypeParser(114, JSONBig.parse); // json
  pgTypes.setTypeParser(3802, JSONBig.parse); // jsonb

  //  install pg-range
  pgRange.install(pg);
})();

const boundToOp = {
  '(': opsMap.gt,
  '[': opsMap.gte,
  ')': opsMap.lt,
  ']': opsMap.lte,
};

const buildTimestampQuery = (column, timestampRange, buildAsIn = true) => {
  const conditions = [];
  const params = [];
  const {range, eqValues, neValues} = timestampRange;

  if (range) {
    const bounds = [...range.bounds];
    if (range.begin) {
      conditions.push(`${column} ${boundToOp[bounds[0]]} ?`);
      params.push(range.begin);
    }
    if (range.end) {
      conditions.push(`${column} ${boundToOp[bounds[1]]} ?`);
      params.push(range.end);
    }
  }

  if (neValues.length) {
    if (buildAsIn) {
      conditions.push(`${column} not in (?`.concat(', ?'.repeat(neValues.length - 1)).concat(')'));
    } else {
      neValues.forEach((_) => conditions.push(`${column} != ?`));
    }
    params.push(...neValues);
  }

  if (eqValues.length) {
    if (buildAsIn) {
      conditions.push(`${column} in (?`.concat(', ?'.repeat(eqValues.length - 1)).concat(')'));
    } else {
      eqValues.forEach((_) => conditions.push(`${column} = ?`));
    }
    params.push(...eqValues);
  }

  return [conditions.join(' and '), params];
};

const buildTimestampRangeQuery = (column, timestampRange) => {
  const conditions = [];
  const params = [];
  const {range, eqValues, neValues} = timestampRange;

  if (range) {
    conditions.push(`${column} && ?`);
    params.push(range);
  }
  if (neValues.length) {
    neValues.forEach((value) => {
      conditions.push(`not ${column} @> ?::bigint`);
      params.push(value);
    });
  }

  if (eqValues.length) {
    eqValues.forEach((value) => {
      conditions.push(`${column} && ?`);
      params.push(Range(null, value, '(]'));
    });
  }

  return [conditions.join(' and '), params];
};

const JSONParse = JSONBig.parse;
const JSONStringify = JSONBig.stringify;

export {
  JSONParse,
  JSONStringify,
  addHexPrefix,
  asNullIfDefault,
  bigIntMax,
  bigIntMin,
  buildAndValidateFilters,
  buildComparatorFilter,
  buildFilters,
  buildTimestampQuery,
  buildTimestampRangeQuery,
  buildPgSqlObject,
  calculateExpiryTimestamp,
  parseTimestampFilters,
  conflictingPathParam,
  convertMySqlStyleQueryToPostgres,
  createTransactionId,
  encodeBase64,
  encodeBinary,
  encodeKey,
  encodeUtf8,
  extractTimestampRangeConditionFilters,
  filterDependencyCheck,
  filterValidityChecks,
  formatComparator,
  formatFilters,
  formatSlot,
  getFirstDayOfMonth,
  getLimitParamValue,
  getNextParamQueries,
  getNullableNumber,
  getPaginationLink,
  getPoolClass,
  getStakingPeriod,
  gtGte,
  incrementTimestampByOneDay,
  ipMask,
  isByteRange,
  isNonNegativeInt32,
  isPositiveLong,
  isRepeatedQueryParameterValidLength,
  isTestEnv,
  isValidEthHash,
  isValidEthHashOrHederaHash,
  isValidUserFileId,
  isValidOperatorQuery,
  isValidPublicKeyQuery,
  isValidSlot,
  isValidTimestampParam,
  isValidValueIgnoreCase,
  lowerCaseQueryValue,
  ltLte,
  mergeParams,
  nowInNs,
  nsToSecNs,
  nsToSecNsWithHyphen,
  opsMap,
  parseAccountIdQueryParam,
  parseBalanceQueryParam,
  parseBooleanValue,
  parseHexStr,
  getEffectiveMaxLimit,
  parseInteger,
  parseLimitAndOrderParams,
  parseParams,
  parsePublicKey,
  parsePublicKeyQueryParam,
  parseTimestampParam,
  parseTimestampQueryParam,
  parseTokenBalances,
  randomString,
  resultSuccess,
  toHexString,
  toHexStringNonQuantity,
  toHexStringQuantity,
  validateAndParseFilters,
  validateFilters,
  validateOpAndValue,
  validateReq,
  stripHexPrefix,
  toUint256,
};
