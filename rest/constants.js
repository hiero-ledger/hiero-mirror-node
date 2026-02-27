// SPDX-License-Identifier: Apache-2.0

const HEX_PREFIX = '0x';
const NANOSECONDS_PER_MILLISECOND = 10n ** 6n;
const NANOS_PER_SECOND = 1_000_000_000n;
const SIXTY_SECONDS = 60n;
const THIRTY_ONE_MINUTES = 31n * 60n;
const MAX_INT32 = 2147483647;
const MAX_LONG = 2n ** 63n - 1n;
const ONE_DAY_IN_NS = 86_400_000_000_000n;
const ZERO_UINT256 = '0x0000000000000000000000000000000000000000000000000000000000000000';
const AUTO_RENEW_PERIOD_MULTIPLE = BigInt(1e9);
const EMPTY_STRING = '';
const EVM_ADDRESS_LENGTH = 20;
const ETH_HASH_LENGTH = 32;

const apiPrefix = '/api/v1';

// url query filer keys
const filterKeys = {
  ACCOUNT_BALANCE: 'account.balance',
  ACCOUNT_ID: 'account.id',
  ACCOUNT_PUBLICKEY: 'account.publickey',
  BALANCE: 'balance',
  BLOCK_HASH: 'block.hash',
  BLOCK_NUMBER: 'block.number',
  CONTRACTID: 'contractid',
  CONTRACT_ID: 'contract.id',
  CREDIT_TYPE: 'type',
  ENCODING: 'encoding',
  ENTITY_PUBLICKEY: 'publickey',
  FILE_ID: 'file.id',
  FROM: 'from',
  HBAR: 'hbar',
  ID_OR_ALIAS_OR_EVM_ADDRESS: 'idOrAliasOrEvmAddress',
  INDEX: 'index',
  INTERNAL: 'internal',
  LIMIT: 'limit',
  NAME: 'name',
  NODE_ID: 'node.id',
  NONCE: 'nonce',
  ORDER: 'order',
  RESULT: 'result',
  SCHEDULED: 'scheduled',
  SCHEDULEID: 'scheduleid',
  SCHEDULE_ID: 'schedule.id',
  SEQUENCE_NUMBER: 'sequencenumber',
  SERIAL_NUMBER: 'serialnumber',
  SPENDER_ID: 'spender.id',
  TIMESTAMP: 'timestamp',
  TOKENID: 'tokenid',
  TOKEN_ID: 'token.id',
  TOKEN_TYPE: 'type',
  TOPIC0: 'topic0',
  TOPIC1: 'topic1',
  TOPIC2: 'topic2',
  TOPIC3: 'topic3',
  TOPIC_ID: 'topic.id',
  TRANSACTION_INDEX: 'transaction.index',
  TRANSACTION_HASH: 'transaction.hash',
  TRANSACTION_TYPE: 'transactiontype',
  TRANSACTIONS: 'transactions',
  HASH_OR_NUMBER: 'hashOrNumber',
  SLOT: 'slot',
};

const entityTypes = {
  ACCOUNT: 'ACCOUNT',
  CONTRACT: 'CONTRACT',
  FILE: 'FILE',
  TOKEN: 'TOKEN',
  TOPIC: 'TOPIC',
  SCHEDULE: 'SCHEDULE',
};

const EvmAddressType = {
  // evm address without shard and realm and with 0x prefix
  NO_SHARD_REALM: 0,
  // evm address with shard and realm as optionals
  OPTIONAL_SHARD_REALM: 1,
  // can be either a NO_SHARD_REALM or OPTIONAL_SHARD_REALM
  ANY: 2,
  // long zero hedera account id
  NUM_ALIAS: 3,
};

const keyTypes = {
  ECDSA_SECP256K1: 'ECDSA_SECP256K1',
  ED25519: 'ED25519',
  PROTOBUF: 'ProtobufEncoded',
};

const contentTypeHeader = 'content-type';
const requestIdLabel = 'requestId';
const requestPathLabel = 'requestPath';
const requestStartTime = 'requestStartTime';
const responseBodyLabel = 'responseBody';
const responseCacheKeyLabel = 'responseCacheKey';
const responseDataLabel = 'responseData';
const userLimitLabel = 'userLimit';

const responseHeadersLabel = 'responseHeaders';

const orderFilterValues = {
  ASC: 'asc',
  DESC: 'desc',
};

// topic messages filter options
const characterEncoding = {
  BASE64: 'base64',
  UTF8: 'utf-8',
};

const transactionResultFilter = {
  SUCCESS: 'success',
  FAIL: 'fail',
};

const cryptoTransferType = {
  CREDIT: 'credit',
  DEBIT: 'debit',
};

const tokenTypeFilter = {
  ALL: 'all',
  FUNGIBLE_COMMON: 'fungible_common',
  NON_FUNGIBLE_UNIQUE: 'non_fungible_unique',
};

const zeroRandomPageCostQueryHint = 'set local random_page_cost = 0';

export class StatusCode {
  constructor(code, message) {
    this.code = code;
    this.message = message;
  }

  isClientError() {
    return this.code >= 400 && this.code < 500;
  }

  toString() {
    return `${this.code} ${this.message}`;
  }
}

const httpStatusCodes = {
  BAD_GATEWAY: new StatusCode(502, 'Bad gateway'),
  BAD_REQUEST: new StatusCode(400, 'Bad request'),
  INTERNAL_ERROR: new StatusCode(500, 'Internal error'),
  NO_CONTENT: new StatusCode(204, 'No content'),
  NOT_FOUND: new StatusCode(404, 'Not found'),
  OK: new StatusCode(200, 'OK'),
  PARTIAL_CONTENT: new StatusCode(206, 'Partial mirror node'),
  SERVICE_UNAVAILABLE: new StatusCode(503, 'Service unavailable'),
  UNAUTHORIZED: new StatusCode(401, 'Unauthorized'),
  UNMODIFIED: new StatusCode(304, 'Not Modified'),
  isSuccess: (code) => code >= 200 && code < 300,
};

const queryParamOperators = {
  eq: 'eq',
  ne: 'ne',
  lt: 'lt',
  lte: 'lte',
  gt: 'gt',
  gte: 'gte',
};

export {
  AUTO_RENEW_PERIOD_MULTIPLE,
  EMPTY_STRING,
  EVM_ADDRESS_LENGTH,
  ETH_HASH_LENGTH,
  HEX_PREFIX,
  NANOSECONDS_PER_MILLISECOND,
  NANOS_PER_SECOND,
  SIXTY_SECONDS,
  THIRTY_ONE_MINUTES,
  MAX_INT32,
  MAX_LONG,
  ONE_DAY_IN_NS,
  ZERO_UINT256,
  apiPrefix,
  characterEncoding,
  contentTypeHeader,
  cryptoTransferType,
  entityTypes,
  filterKeys,
  httpStatusCodes,
  keyTypes,
  orderFilterValues,
  queryParamOperators,
  requestIdLabel,
  requestPathLabel,
  requestStartTime,
  responseBodyLabel,
  responseCacheKeyLabel,
  responseDataLabel,
  responseHeadersLabel,
  userLimitLabel,
  tokenTypeFilter,
  transactionResultFilter,
  zeroRandomPageCostQueryHint,
  EvmAddressType,
};
