// SPDX-License-Identifier: Apache-2.0

import * as math from 'mathjs';
import config from './config';

import {
  accountIdCompare,
  checkEntityId,
  checkAPIResponseError,
  checkElementsOrder,
  checkMandatoryParams,
  checkResourceFreshness,
  checkRespArrayLength,
  checkRespObjDefined,
  CheckRunner,
  DEFAULT_LIMIT,
  fetchAPIResponse,
  getUrl,
  hasEmptyList,
  testRunner,
} from './utils';

const tokensPath = '/tokens';
const resource = 'token';
const tokensLimit = config[resource].limit || DEFAULT_LIMIT;
const tokenIdFromConfig = config[resource].tokenId;
const nftIdFromConfig = config[resource].nftTokenId;
const tokensJsonRespKey = 'tokens';
const tokenMandatoryParams = ['token_id', 'symbol', 'admin_key'];

/**
 * Verifies base tokens call
 *
 * @param {String} server API host endpoint
 * @return {Promise<{message: String, passed: boolean, url: String}>}
 */
const getTokensCheck = async (server) => {
  const url = getUrl(server, tokensPath, {limit: tokensLimit});
  const tokens = await fetchAPIResponse(url, tokensJsonRespKey);

  const result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'tokens is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: tokensLimit,
      message: (elements, limit) => `tokens.length of ${elements.length} is less than limit ${limit}`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: tokenMandatoryParams,
      message: 'token object is missing some mandatory fields',
    })
    .run(tokens);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called tokens',
  };
};

const getFirstTokenIdWithCheckResult = async (server) => {
  const url = getUrl(server, tokensPath, {limit: 1});
  const tokens = await fetchAPIResponse(url, tokensJsonRespKey);

  const result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'tokens is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: 1,
      message: (elements) => `tokens.length of ${elements.length} was expected to be 1`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: tokenMandatoryParams,
      message: 'token object is missing some mandatory fields',
    })
    .run(tokens);
  return {
    tokenId: result.passed ? tokens[0].token_id : null,
    result: {
      url,
      ...result,
    },
  };
};

const getFirstTokenIdForNft = async (server) => {
  const url = getUrl(server, tokensPath, {type: 'NON_FUNGIBLE_UNIQUE', limit: 1});
  const tokens = await fetchAPIResponse(url, tokensJsonRespKey);

  const result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'tokens is undefined'})
    .withCheckSpec(checkMandatoryParams, {
      params: tokenMandatoryParams,
      message: 'token object is missing some mandatory fields',
    })
    .run(tokens);
  return {
    tokenId: result.passed ? tokens[0].token_id : null,
    result: {
      url,
      ...result,
    },
  };
};

/**
 * Verifies tokens call with limit param
 *
 * @param server API host endpoint
 * @return {Promise<{message: String, passed: boolean, url: String}>}
 */
const getTokensWithLimitParam = async (server) => {
  const {result} = await getFirstTokenIdWithCheckResult(server);
  if (!result.passed) {
    return result;
  }

  return {
    ...result,
    message: 'Successfully called tokens',
  };
};

/**
 * Verifies tokens call with order 'asc' and 'desc'
 *
 * @param server API host endpoint
 * @return {Promise<{message: String, passed: boolean, url: String}>}
 */
const getTokensWithOrderParam = async (server) => {
  let url = getUrl(server, tokensPath, {order: 'asc'});
  let tokens = await fetchAPIResponse(url, tokensJsonRespKey);

  const checkRunner = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'tokens is undefined'})
    .withCheckSpec(checkMandatoryParams, {
      params: tokenMandatoryParams,
      message: 'token object is missing some mandatory fields',
    })
    .withCheckSpec(checkElementsOrder, {asc: true, compare: accountIdCompare, key: 'token_id', name: 'token ID'});
  let result = checkRunner.run(tokens);
  if (!result.passed) {
    return {url, ...result};
  }

  url = getUrl(server, tokensPath, {order: 'desc'});
  tokens = await fetchAPIResponse(url, tokensJsonRespKey);

  result = checkRunner
    .resetCheckSpec(checkElementsOrder, {asc: false, compare: accountIdCompare, key: 'token_id', name: 'token ID'})
    .run(tokens);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called tokens with order "asc" and "desc"',
  };
};

const tokenInfoPath = (tokenId) => `${tokensPath}/${tokenId}`;
const tokenInfoMandatoryParams = [
  'token_id',
  'symbol',
  'admin_key',
  'auto_renew_account',
  'auto_renew_period',
  'created_timestamp',
  'decimals',
  'expiry_timestamp',
  'freeze_default',
  'freeze_key',
  'initial_supply',
  'kyc_key',
  'modified_timestamp',
  'name',
  'supply_key',
  'total_supply',
  'treasury_account_id',
  'wipe_key',
];

/**
 * Verifies token info call.
 *
 * @param server
 * @return {Promise<{message: String, passed: boolean, url: String}>}
 */
const getTokenInfoCheck = async (server) => {
  let tokenId = tokenIdFromConfig;
  if (!tokenId) {
    const {tokenId: tokenIdFromAPI, result} = await getFirstTokenIdWithCheckResult(server);
    if (!result.passed) {
      return result;
    }

    tokenId = tokenIdFromAPI;
  }

  const url = getUrl(server, tokenInfoPath(tokenId));
  const tokenInfo = await fetchAPIResponse(url);

  const tokenInfoResult = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'token info is undefined'})
    .withCheckSpec(checkMandatoryParams, {
      params: tokenInfoMandatoryParams,
      message: 'token info object is missing some mandatory fields',
    })
    .run(tokenInfo);
  if (!tokenInfoResult.passed) {
    return {url, ...tokenInfoResult};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called token info',
  };
};

const tokenBalancesJsonRespKey = 'balances';
const tokenBalanceMandatoryParams = ['account', 'balance'];
const tokenBalancesPath = (tokenId) => `${tokensPath}/${tokenId}/balances`;

/**
 * Verifies token balances call.
 *
 * @param server
 * @return {Promise<{message: String, passed: boolean, url: String}>}
 */
const getTokenBalancesCheck = async (server) => {
  let tokenId = tokenIdFromConfig;
  if (!tokenId) {
    const {tokenId: tokenIdFromAPI, result} = await getFirstTokenIdWithCheckResult(server);
    if (!result.passed) {
      return result;
    }

    tokenId = tokenIdFromAPI;
  }

  const url = getUrl(server, tokenBalancesPath(tokenId), {limit: tokensLimit});
  const balances = await fetchAPIResponse(url, tokenBalancesJsonRespKey);

  const balancesResult = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'token balances is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: tokensLimit,
      message: (elements, limit) => `token balances.length of ${elements.length} is less than limit ${limit}`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: tokenBalanceMandatoryParams,
      message: 'token balance object is missing some mandatory fields',
    })
    .run(balances);
  if (!balancesResult.passed) {
    return {url, ...balancesResult};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called token balances',
  };
};

/**
 * Verifies token balances call with limit param.
 *
 * @param server
 * @return {Promise<{message: String, passed: boolean, url: String}>}
 */
const getTokenBalancesWithLimitParam = async (server) => {
  let tokenId = tokenIdFromConfig;
  if (!tokenId) {
    const {tokenId: tokenIdFromAPI, result} = await getFirstTokenIdWithCheckResult(server);
    if (!result.passed) {
      return result;
    }

    tokenId = tokenIdFromAPI;
  }

  const url = getUrl(server, tokenBalancesPath(tokenId), {limit: 1});
  const balances = await fetchAPIResponse(url, tokenBalancesJsonRespKey);

  const balancesResult = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'token balances is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: 1,
      message: (elements) => `token balances.length of ${elements.length} was expected to be 1`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: tokenBalanceMandatoryParams,
      message: 'token balance object is missing some mandatory fields',
    })
    .run(balances);
  if (!balancesResult.passed) {
    return {url, ...balancesResult};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called token balances with limit param',
  };
};

/**
 * Verifies token balances call with timestamp param.
 *
 * @param server
 * @return {Promise<{message: String, passed: boolean, url: String}>}
 */
const getTokenBalancesWithTimestampParam = async (server) => {
  let tokenId = tokenIdFromConfig;
  if (!tokenId) {
    const {tokenId: tokenIdFromAPI, result} = await getFirstTokenIdWithCheckResult(server);
    if (!result.passed) {
      return result;
    }

    tokenId = tokenIdFromAPI;
  }

  let url = getUrl(server, tokenBalancesPath(tokenId), {limit: 1});
  const resp = await fetchAPIResponse(url);
  let balances = resp instanceof Error ? resp : resp[tokenBalancesJsonRespKey];
  const checkRunner = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'balances is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: 1,
      message: (elements) => `token balances.length of ${elements.length} was expected to be 1`,
    });
  let balancesResult = checkRunner.run(balances);
  if (!balancesResult.passed) {
    return {url, ...balancesResult};
  }

  const {timestamp} = resp;

  const minusOne = math.subtract(math.bignumber(timestamp), math.bignumber(1));
  url = getUrl(server, tokenBalancesPath(tokenId), {
    timestamp: [`gt:${minusOne.toString()}`],
    limit: 1,
  });

  balances = await fetchAPIResponse(url, tokenBalancesJsonRespKey);
  balancesResult = checkRunner.run(balances);

  if (!balancesResult.passed) {
    return {url, ...balancesResult};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called token balances with timestamp param',
  };
};

/**
 * Verifies token balances call for a single account.
 *
 * @param server
 * @return {Promise<{message: String, passed: boolean, url: String}>}
 */
const getTokenBalancesForAccount = async (server) => {
  let tokenId = tokenIdFromConfig;
  if (!tokenId) {
    const {tokenId: tokenIdFromAPI, result} = await getFirstTokenIdWithCheckResult(server);
    if (!result.passed) {
      return result;
    }

    tokenId = tokenIdFromAPI;
  }

  let url = getUrl(server, tokenBalancesPath(tokenId), {limit: 1});
  let balances = await fetchAPIResponse(url, tokenBalancesJsonRespKey, hasEmptyList(tokenBalancesJsonRespKey));

  const checkRunner = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'balances is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: 1,
      message: (elements) => `token balances.length of ${elements.length} was expected to be 1`,
    });
  let balancesResult = checkRunner.run(balances);
  if (!balancesResult.passed) {
    return {url, ...balancesResult};
  }

  const accountId = balances[0].account;
  url = getUrl(server, tokenBalancesPath(tokenId), {'account.id': accountId});
  balances = await fetchAPIResponse(url, tokenBalancesJsonRespKey);

  balancesResult = checkRunner
    .withCheckSpec(checkEntityId, {accountId, message: 'account was not found'})
    .run(balances);
  if (!balancesResult.passed) {
    return {url, ...balancesResult};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called token balances and performed account check',
  };
};

async function getNfts(url, tokenNftsJsonRespKey) {
  let nfts = await fetchAPIResponse(url, tokenNftsJsonRespKey);
  let nftsResult = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'nfts is undefined'})
    .run(nfts);

  return {
    response: nfts,
    result: nftsResult,
  };
}

/**
 * Verifies token /nfts and /nfts/{serialNumber} and /nfts/{serialNumber}/transactions calls for a token.
 *
 * @param server
 * @return {Promise<{message: String, passed: boolean, url: String}>}
 */
const getTokenNfts = async (server) => {
  let tokenId = nftIdFromConfig;
  const tokenNftsJsonRespKey = 'nfts';
  if (!tokenId) {
    const {tokenId: tokenIdFromAPI, result} = await getFirstTokenIdForNft(server);
    if (!result.passed) {
      return result;
    }

    tokenId = tokenIdFromAPI;
  }

  const tokenNftsPath = `${tokensPath}/${tokenId}/nfts`;

  let url = getUrl(server, tokenNftsPath, {limit: 1});
  let nfts = await getNfts(url, tokenNftsJsonRespKey);
  let nftsResult = nfts.result;
  if (!nftsResult.passed) {
    return {url, ...nftsResult};
  }

  if (!nfts.length) {
    return {
      url,
      passed: true,
      message: 'Successfully called token nfts with empty list',
    };
  }

  const serialNumber = nfts.response[0].serial_number;
  url = getUrl(server, tokenNftsPath + `/${serialNumber}`);

  nfts = await getNfts(url, '');
  nftsResult = nfts.result;
  if (!nftsResult.passed) {
    return {url, ...nftsResult};
  }

  url = getUrl(server, tokenNftsPath + `/${serialNumber}/transactions`);
  nfts = await getNfts(url, 'transactions');
  nftsResult = nfts.result;

  if (!nftsResult.passed) {
    return {url, ...nftsResult};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called token nfts',
  };
};

/**
 * Verifies the freshness of token balances
 *
 * @param server
 * @return {Promise<{message: String, passed: boolean, url: String}>}
 */
const checkTokenBalanceFreshness = async (server) => {
  let tokenId = tokenIdFromConfig;
  if (!tokenId) {
    const {tokenId: tokenIdFromAPI, result} = await getFirstTokenIdWithCheckResult(server);
    if (!result.passed) {
      return result;
    }

    tokenId = tokenIdFromAPI;
  }

  return checkResourceFreshness(server, tokenBalancesPath(tokenId), resource, (data) => data.timestamp);
};

/**
 * Run all token tests in an asynchronous fashion waiting for all tests to complete
 *
 * @param {Object} server object provided by the user
 * @param {ServerTestResult} testResult shared server test result object capturing tests for given endpoint
 */
const runTests = async (server, testResult) => {
  if (!tokenIdFromConfig) {
    return Promise.resolve();
  }

  const runTest = testRunner(server, testResult, resource);
  return Promise.all([
    runTest(getTokensCheck),
    runTest(getTokensWithLimitParam),
    runTest(getTokensWithOrderParam),
    runTest(getTokenInfoCheck),
    runTest(getTokenBalancesCheck),
    runTest(getTokenBalancesWithLimitParam),
    runTest(getTokenBalancesWithTimestampParam),
    runTest(getTokenBalancesForAccount),
    runTest(checkTokenBalanceFreshness),
    runTest(getTokenNfts),
  ]);
};

export default {
  resource,
  runTests,
};
