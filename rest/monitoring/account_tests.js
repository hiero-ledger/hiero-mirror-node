// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';
import config from './config';

import {
  checkEntityId,
  checkAPIResponseError,
  checkMandatoryParams,
  checkRespArrayLength,
  checkRespObjDefined,
  CheckRunner,
  DEFAULT_LIMIT,
  fetchAPIResponse,
  getUrl,
  hasEmptyList,
  testRunner,
} from './utils';

const accountsPath = '/accounts';
const resource = 'account';
const resourceLimit = config[resource].limit || DEFAULT_LIMIT;
const cryptoAllowanceOwnerId = config[resource].cryptoAllowanceOwnerId;
const nftAccountId = config[resource].nftAccountId;
const nftAllowanceOwnerId = config[resource].nftAllowanceOwnerId;
const stakingRewardAccountId = config[resource].stakingRewardAccountId;
const tokenAllowanceOwnerId = config[resource].tokenAllowanceOwnerId;
const tokenRelationshipEnabled = config[resource].tokenRelationshipEnabled || false;
const jsonRespKey = 'accounts';
const allowancesJsonRespKey = 'allowances';
const mandatoryParams = [
  'balance',
  'account',
  'expiry_timestamp',
  'auto_renew_period',
  'key',
  'deleted',
  'balance.timestamp',
  'balance.balance',
];

/**
 * Verify base accounts call
 * Also ensure an account mentioned in the accounts can be confirmed as existing
 * @param {Object} server API host endpoint
 * @returns {{url: string, passed: boolean, message: string}}
 */
const getAccountsWithAccountCheck = async (server) => {
  let url = getUrl(server, accountsPath, {limit: resourceLimit});
  const accounts = await fetchAPIResponse(url, jsonRespKey);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'account is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: resourceLimit,
      message: (accts, limit) => `accounts.length of ${accts.length} is less than limit ${limit}`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'account object is missing some mandatory fields',
    })
    .run(accounts);
  if (!result.passed) {
    return {url, ...result};
  }

  const highestAccount = _.max(_.map(accounts, (acct) => acct.account));
  url = getUrl(server, accountsPath, {
    'account.id': highestAccount,
    limit: 1,
  });
  const singleAccount = await fetchAPIResponse(url, jsonRespKey, hasEmptyList(jsonRespKey));

  result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'singleAccount is undefined'})
    .withCheckSpec(checkEntityId, {accountId: highestAccount, message: 'Highest acc check was not found'})
    .run(singleAccount);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called accounts and performed account check',
  };
};

async function getAccounts(server) {
  let url = getUrl(server, accountsPath, {limit: resourceLimit});
  const accounts = await fetchAPIResponse(url, jsonRespKey);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'accounts is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: resourceLimit,
      message: (accts, limit) => `accounts.length of ${accts.length} was expected to be ${limit}`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'account object is missing some mandatory fields',
    })
    .run(accounts);
  return {url, accounts, result};
}

/**
 * Verify single account can be retrieved
 * @param {Object} server API host endpoint
 */
const getSingleAccount = async (server) => {
  let {url, accounts, result} = await getAccounts(server);
  if (!result.passed) {
    return {url, ...result};
  }

  const highestAccount = _.max(_.map(accounts, (acct) => acct.account));
  url = getUrl(server, `${accountsPath}/${highestAccount}`);
  const singleAccount = await fetchAPIResponse(url);

  result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'accounts is undefined'})
    .withCheckSpec(checkEntityId, {accountId: highestAccount, message: 'Highest account number was not found'})
    .run(singleAccount);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called accounts for single account',
  };
};

/**
 * Verify that we return token relationships for an existing account
 * @param {Object} server API host endpoint
 */
const getSingleAccountTokenRelationships = async (server) => {
  // Call the get /tokens endpoint to get a list of tokens.
  const tokensPath = '/tokens';
  const tokensJsonRespKey = 'tokens';
  const tokensLimit = 1;
  const tokenMandatoryParams = ['token_id'];
  const token_url = getUrl(server, tokensPath, {limit: tokensLimit, order: 'asc'});
  const tokens = await fetchAPIResponse(token_url, tokensJsonRespKey);

  const tokenResult = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'tokens is undefined'})
    .withCheckSpec(checkMandatoryParams, {
      params: tokenMandatoryParams,
      message: 'token object is missing some mandatory fields',
    })
    .run(tokens);
  if (tokens.length === 0) {
    tokenResult.passed = true;
    tokenResult.message = 'No tokens were returned';
    return {url: token_url, ...tokenResult};
  }
  if (!tokenResult.passed) {
    return {url: token_url, ...tokenResult};
  }

  // get balances for a token and retrieve an  account id from it.

  const token_id = tokens[0].token_id;
  const tokenBalancesPath = (tokenId) => `${tokensPath}/${tokenId}/balances`;
  const tokenBalancesJsonRespKey = 'balances';

  const balancesLimit = 1;
  let balances_url = getUrl(server, tokenBalancesPath(token_id), {limit: balancesLimit});
  let balances = await fetchAPIResponse(balances_url, tokenBalancesJsonRespKey);
  const checkRunner = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'balances is undefined'});
  let balancesResult = checkRunner.run(balances);
  if (balances.length === 0) {
    balancesResult.passed = true;
    balancesResult.message = 'No balances were returned';
    return {url: balances_url, ...balancesResult};
  }
  if (!balancesResult.passed) {
    return {url: balances_url, ...balancesResult};
  }
  const accountId = balances[0].account;

  // Use that account id to call the /accounts/${accountId}/tokens endpoint
  const accountsTokenPath = `/accounts/${accountId}/tokens`;
  const accountsLimit = 1;
  let url = getUrl(server, accountsTokenPath, {limit: accountsLimit, order: 'asc'});
  const tokenRelationships = await fetchAPIResponse(url, tokensJsonRespKey, hasEmptyList(tokensJsonRespKey));
  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'tokens is undefined '})
    .withCheckSpec(checkRespArrayLength, {
      limit: accountsLimit,
      message: (accts, limit) => `tokens.length of ${accts.length} was expected to be ${limit}`,
    })
    .run(tokenRelationships);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called accounts for token relationships',
  };
};

const getAccountStakingRewards = async (server) => {
  let accountId = stakingRewardAccountId;
  if (!stakingRewardAccountId) {
    let {url, accounts, result} = await getAccounts(server);
    if (!result.passed) {
      return {url, ...result};
    }

    accountId = _.max(_.map(accounts, (acct) => acct.account));
  }

  const stakingRewardsPath = `${accountsPath}/${accountId}/rewards`;
  const rewardsJsonRespKey = 'rewards';
  let url = getUrl(server, stakingRewardsPath, {limit: resourceLimit});
  const rewardMandatoryParams = ['account_id', 'amount', 'timestamp'];
  const rewards = await fetchAPIResponse(url, rewardsJsonRespKey);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'rewards is undefined'})
    .withCheckSpec(checkMandatoryParams, {
      params: rewardMandatoryParams,
      message: 'reward object is missing some mandatory fields',
    })
    .run(rewards);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called account staking rewards for account ${stakingRewardAccountId}.',
  };
};

/**
 * Verify that we return crypto allowances for an existing account
 * @param {Object} server API host endpoint
 */
const getCryptoAllowances = async (server) => {
  let accountId = cryptoAllowanceOwnerId;
  if (!cryptoAllowanceOwnerId) {
    let {url, accounts, result} = await getAccounts(server);
    if (!result.passed) {
      return {url, ...result};
    }

    accountId = _.max(_.map(accounts, (acct) => acct.account));
  }

  const cryptoAllowancesPath = `${accountsPath}/${accountId}/allowances/crypto`;
  let url = getUrl(server, cryptoAllowancesPath, {limit: resourceLimit});
  const allowances = await fetchAPIResponse(url, allowancesJsonRespKey);
  const allowancesMandatoryParams = ['amount', 'amount_granted', 'owner', 'spender', 'timestamp'];

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'crypto allowances is undefined'})
    .withCheckSpec(checkMandatoryParams, {
      params: allowancesMandatoryParams,
      message: 'crypto allowances object is missing some mandatory fields',
    })
    .run(allowances);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: `Successfully called crypto allowances for account ${cryptoAllowanceOwnerId}.`,
  };
};

/**
 * Verify that we return token allowances for an existing account
 * @param {Object} server API host endpoint
 */
const getTokenAllowances = async (server) => {
  let accountId = tokenAllowanceOwnerId;
  if (!tokenAllowanceOwnerId) {
    let {url, accounts, result} = await getAccounts(server);
    if (!result.passed) {
      return {url, ...result};
    }

    accountId = _.max(_.map(accounts, (acct) => acct.account));
  }

  const tokenAllowancesPath = `${accountsPath}/${accountId}/allowances/tokens`;
  let url = getUrl(server, tokenAllowancesPath, {limit: resourceLimit});
  const allowances = await fetchAPIResponse(url, allowancesJsonRespKey);
  const tokenAllowancesMandatoryParams = ['amount', 'amount_granted', 'owner', 'spender', 'timestamp', 'token_id'];

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'token allowances is undefined'})
    .withCheckSpec(checkMandatoryParams, {
      params: tokenAllowancesMandatoryParams,
      message: 'token allowances object is missing some mandatory fields',
    })
    .run(allowances);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: `Successfully called token allowances for account ${tokenAllowanceOwnerId}.`,
  };
};

/**
 * Verify that we return nft allowances for an existing account
 * @param {Object} server API host endpoint
 */
const getNftAllowances = async (server) => {
  const nftAllowancesPath = `${accountsPath}/${nftAllowanceOwnerId}/allowances/nfts`;
  let url = getUrl(server, nftAllowancesPath, {limit: resourceLimit});
  const allowances = await fetchAPIResponse(url, allowancesJsonRespKey);
  const nftAllowancesMandatoryParams = ['approved_for_all', 'owner', 'spender', 'timestamp', 'token_id'];

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'nft allowances is undefined'})
    .withCheckSpec(checkMandatoryParams, {
      params: nftAllowancesMandatoryParams,
      message: 'nft allowances object is missing some mandatory fields',
    })
    .run(allowances);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: `Successfully called nft allowances for account ${nftAllowanceOwnerId}.`,
  };
};

/**
 * Verify that we return nfts for an existing account
 * @param {Object} server API host endpoint
 */
const getNfts = async (server) => {
  let accountId = nftAccountId;
  if (!nftAccountId) {
    let {url, accounts, result} = await getAccounts(server);
    if (!result.passed) {
      return {url, ...result};
    }

    accountId = _.max(_.map(accounts, (acct) => acct.account));
  }

  const nftsPath = `${accountsPath}/${accountId}/nfts`;
  let url = getUrl(server, nftsPath, {limit: resourceLimit});
  const nftJsonResponseKey = 'nfts';
  const allowances = await fetchAPIResponse(url, nftJsonResponseKey);
  const nftsMandatoryParams = [
    'account_id',
    'created_timestamp',
    'delegating_spender',
    'deleted',
    'metadata',
    'modified_timestamp',
    'serial_number',
    'spender',
    'token_id',
  ];

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'nfts is undefined'})
    .withCheckSpec(checkMandatoryParams, {
      params: nftsMandatoryParams,
      message: 'nfts object is missing some mandatory fields',
    })
    .run(allowances);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: `Successfully called nfts for account ${nftAccountId}.`,
  };
};

/**
 * Run all account tests in an asynchronous fashion waiting for all tests to complete
 * @param {Object} server object provided by the user
 * @param {ServerTestResult} testResult shared server test result object capturing tests for given endpoint
 */
const runTests = async (server, testResult) => {
  const runTest = testRunner(server, testResult, resource);
  return Promise.all([
    runTest(getAccountsWithAccountCheck),
    runTest(getSingleAccount),
    tokenRelationshipEnabled ? runTest(getSingleAccountTokenRelationships) : '',
    runTest(getAccountStakingRewards),
    runTest(getCryptoAllowances),
    runTest(getTokenAllowances),
    nftAllowanceOwnerId !== null ? runTest(getNftAllowances, 'REST_JAVA') : '',
    runTest(getNfts),
  ]);
};

export default {
  resource,
  runTests,
};
