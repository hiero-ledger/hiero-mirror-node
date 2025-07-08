// SPDX-License-Identifier: Apache-2.0

import {b64decode, b64encode} from 'k6/encoding';
import http from 'k6/http';

import {
  computeTestParameters,
  getValidResponse,
  setEnvDefault,
  wrapComputeParametersFunc,
} from '../../lib/parameters.js';

import {
  accountListName,
  allowanceListName,
  balanceListName,
  blockListName,
  contractListName,
  messageListName,
  resultListName,
  scheduleListName,
  tokenListName,
  transactionListName,
} from './constants.js';

const baseUrl = __ENV.BASE_URL;
const baseUrlPrefix = __ENV.BASE_URL_PREFIX;

setEnvDefault('DEFAULT_BALANCE_TIMESTAMP', Date.now() / 1000);
setEnvDefault('DEFAULT_TOKEN_BALANCE_TIMESTAMP', Date.now() / 1000);
setEnvDefault('DEFAULT_TOKEN_TIMESTAMP', Date.now() / 1000);
setEnvDefault('DEFAULT_START_ACCOUNT', 0);

const restUrlFromNext = (next) => next && `${baseUrl}${next}`;

const last = (arr) => arr[arr.length - 1];

const getEntities = (listEntityPath, key) => {
  const body = getValidResponse(listEntityPath, null, http.get);
  const entities = body[key];
  if (entities == null) {
    throw new Error(`Missing ${key} property in ${listEntityPath} response`);
  }

  return entities;
};

const getFirstEntity = (listEntityPath, key) => {
  const entities = getEntities(listEntityPath, key);
  if (entities.length === 0) {
    throw new Error(`No ${key} were found in the response for request at ${listEntityPath}`);
  }
  return entities[0];
};

const getPropertiesForEntity = (extractProperties, properties) => {
  const {entitiesKey, subResourcePath} = properties;
  const queryParamMap = Object.assign({limit: 100, order: 'desc'}, properties.queryParamMap || {});
  const queryParams = Object.entries(queryParamMap)
    .map(([key, val]) => `${key}=${val}`)
    .join('&');
  const defaultEntitiesQuery = `${baseUrlPrefix}/${entitiesKey}?${queryParams}`;
  const entityIdPaginationKey = `${entitiesKey.substring(0, entitiesKey.length - 1)}.id`;
  const entityIdResponseKey = properties.entityIdResponseKey || entityIdPaginationKey.replace('.', '_');
  const subResourceKey = subResourcePath && last(subResourcePath.split('/'));

  let lastEntityId = '';

  while (true) {
    const query = `${defaultEntitiesQuery}${lastEntityId && entityIdPaginationKey + '=lt:' + lastEntityId}`;
    const entities = getEntities(query, entitiesKey.includes('/') ? resultListName : entitiesKey);
    if (entities.length === 0) {
      throw new Error('No ${entitiesKey} matching criteria found');
    }
    for (const entity of entities) {
      try {
        lastEntityId = entity[entityIdResponseKey];
        const args = [entity];

        if (subResourcePath) {
          const query = `${baseUrlPrefix}/${entitiesKey}/${lastEntityId}/${subResourcePath}?limit=1&order=desc`;
          const subResource = getFirstEntity(query, subResourceKey);
          args.push(subResource);
        }

        return extractProperties(...args);
      } catch (err) {
        // Ignore the error and continue to the next entity
      }
    }
  }
};

const computeAccountParameters = wrapComputeParametersFunc(
  ['DEFAULT_ACCOUNT_ID', 'DEFAULT_ACCOUNT_BALANCE', 'DEFAULT_PUBLIC_KEY', 'DEFAULT_ACCOUNT_ID_TIMESTAMP'],
  () => {
    const extractProperties = (account) => {
      const {
        balance: {balance},
        key,
        created_timestamp: timestamp,
      } = account;
      if (key === null) {
        throw new Error('The account has no key');
      }

      if (balance === 0) {
        throw new Error('The account has a zero balance');
      }

      return {
        DEFAULT_ACCOUNT_BALANCE: balance,
        DEFAULT_ACCOUNT_ID: account.account,
        DEFAULT_PUBLIC_KEY: key.key,
        DEFAULT_ACCOUNT_ID_TIMESTAMP: timestamp,
      };
    };
    return getPropertiesForEntity(extractProperties, {
      entitiesKey: accountListName,
      entityIdResponseKey: 'account',
    });
  }
);

const computeAccountWithCryptoAllowanceParameters = wrapComputeParametersFunc(
  ['DEFAULT_ACCOUNT_ID_CRYPTO_ALLOWANCE', 'DEFAULT_SPENDER_ID_CRYPTO_ALLOWANCE'],
  () => {
    let accountsPath = `${baseUrlPrefix}/accounts?account.id=gt:${__ENV.DEFAULT_START_ACCOUNT}&balance=false&order=asc&limit=100`;
    const candidate = {cryptoAllowances: []};
    let totalAccounts = 0;

    while (accountsPath && totalAccounts < 100000) {
      const {
        accounts,
        links: {next},
      } = getValidResponse(accountsPath, null, http.get);
      accountsPath = restUrlFromNext(next);

      for (const {account} of accounts) {
        const cryptoAllowancesPath = `${baseUrlPrefix}/accounts/${account}/allowances/crypto?limit=25`;
        const cryptoAllowances = getEntities(cryptoAllowancesPath, allowanceListName);

        if (cryptoAllowances.length > candidate.cryptoAllowances.length) {
          candidate.account = account;
          candidate.cryptoAllowances = cryptoAllowances;
        }

        if (cryptoAllowances.length >= 25) {
          console.info(`Found account ${account} with ${cryptoAllowances.length} crypto allowances`);
          return {
            DEFAULT_ACCOUNT_ID_CRYPTO_ALLOWANCE: account,
            DEFAULT_SPENDER_ID_CRYPTO_ALLOWANCE: cryptoAllowances[0].spender,
          };
        }
      }

      totalAccounts += accounts.length;
    }

    if (candidate.length > 0) {
      console.warn(
        `Fallback to account ${candidate.account} with ${candidate.cryptoAllowances.length} crypto allowances`
      );
      return {
        DEFAULT_ACCOUNT_ID_CRYPTO_ALLOWANCE: candidate.account,
        DEFAULT_SPENDER_ID_CRYPTO_ALLOWANCE: candidate.cryptoAllowances[0].spender,
      };
    }

    throw new Error('It was not possible to find an account with significant number of crypto allowances.');
  }
);

const computeAccountWithNftsParameters = wrapComputeParametersFunc(['DEFAULT_ACCOUNT_ID_NFTS'], () => {
  const candidate = {balance: 0};
  let tokensPath = `${baseUrlPrefix}/tokens?type=NON_FUNGIBLE_UNIQUE&limit=100&order=asc`;
  let totalTokens = 0;

  while (tokensPath && totalTokens < 100000) {
    const {
      tokens,
      links: {next},
    } = getValidResponse(tokensPath, null, http.get);
    tokensPath = restUrlFromNext(next);

    for (const {token_id: tokenId} of tokens) {
      const tokensBalancePath = `${baseUrlPrefix}/tokens/${tokenId}/balances?limit=100`;
      const tokensBalances = getEntities(tokensBalancePath, balanceListName);
      for (const tokenBalance of tokensBalances) {
        if (tokenBalance.balance > candidate.balance) {
          Object.assign(candidate, tokenBalance, {token: tokenId});
        }

        if (tokenBalance.balance >= 25) {
          console.info(`Found account ${tokenBalance.account} with ${tokenBalance.balance} ${tokenId} nfts`);
          return {DEFAULT_ACCOUNT_ID_NFTS: tokenBalance.account};
        }
      }
    }

    totalTokens += tokens.length;
  }

  if (candidate.balance > 0) {
    console.warn(`Fallback to account ${candidate.account} with ${candidate.balance} ${candidate.token} nfts`);
    return {DEFAULT_ACCOUNT_ID_NFTS: candidate.account};
  }

  throw new Error('It was not possible to find an account with significant number of nfts.');
});

const computeAccountWithTokenAllowanceParameters = wrapComputeParametersFunc(
  ['DEFAULT_ACCOUNT_ID_TOKEN_ALLOWANCE'],
  () => {
    let accountsPath = `${baseUrlPrefix}/accounts?account.id=gt:${__ENV.DEFAULT_START_ACCOUNT}&balance=false&order=asc&limit=100`;
    const candidate = {length: 0};
    let totalAccounts = 0;

    while (accountsPath && totalAccounts < 100000) {
      const {
        accounts,
        links: {next},
      } = getValidResponse(accountsPath, null, http.get);
      accountsPath = restUrlFromNext(next);

      for (const {account} of accounts) {
        const tokensAllowancesPath = `${baseUrlPrefix}/accounts/${account}/allowances/tokens?limit=25`;
        const tokensAllowances = getEntities(tokensAllowancesPath, allowanceListName);

        if (tokensAllowances.length > candidate.length) {
          candidate.account = account;
          candidate.length = tokensAllowances.length;
        }

        if (tokensAllowances.length >= 25) {
          console.info(`Found account ${account} with ${tokensAllowances.length} token allowances`);
          return {DEFAULT_ACCOUNT_ID_TOKEN_ALLOWANCE: account};
        }
      }

      totalAccounts += accounts.length;
    }

    if (candidate.length > 0) {
      console.warn(`Fallback to account ${candidate.account} with ${candidate.length} token allowances`);
      return {DEFAULT_ACCOUNT_ID_TOKEN_ALLOWANCE: candidate.account};
    }

    throw new Error('It was not possible to find an account with significant number of token allowances.');
  }
);

const computeAccountWithTokenParameters = wrapComputeParametersFunc(['DEFAULT_ACCOUNT_ID_TOKEN'], () => {
  let accountsPath = `${baseUrlPrefix}/accounts?limit=100`;
  const candidate = {length: 0};
  let totalAccounts = 0;

  while (accountsPath && totalAccounts < 100000) {
    const {
      accounts,
      links: {next},
    } = getValidResponse(accountsPath, null, http.get);
    accountsPath = restUrlFromNext(next);

    for (const {account} of accounts) {
      const tokensPath = `${baseUrlPrefix}/accounts/${account}/tokens?limit=25`;
      const tokensEntities = getEntities(tokensPath, tokenListName);

      if (candidate.length < tokensEntities.length) {
        candidate.account = account;
        candidate.length = tokensEntities.length;
      }

      if (tokensEntities.length >= 25) {
        console.info(`Found account ${account} with ${tokensEntities.length} tokens`);
        return {DEFAULT_ACCOUNT_ID_TOKEN: account};
      }
    }

    totalAccounts += accounts.length;
  }

  if (candidate.length > 0) {
    console.warn(`Fallback to account ${candidate.account} with ${candidate.length} tokens`);
    return {DEFAULT_ACCOUNT_ID_TOKEN: candidate.account};
  }

  throw new Error('It was not possible to find an account with significant number of tokens.');
});

const computeBlockParameters = wrapComputeParametersFunc(['DEFAULT_BLOCK_NUMBER', 'DEFAULT_BLOCK_HASH'], () => {
  const extractProperties = (block) => {
    return {
      DEFAULT_BLOCK_NUMBER: block.number,
      DEFAULT_BLOCK_HASH: block.hash,
    };
  };
  return getPropertiesForEntity(extractProperties, {
    entitiesKey: blockListName,
    queryParamMap: {limit: 1},
  });
});

const computeContractParameters = wrapComputeParametersFunc(
  ['DEFAULT_CONTRACT_ID', 'DEFAULT_CONTRACT_TIMESTAMP', 'DEFAULT_CONTRACT_RESULT_HASH'],
  () => {
    const extractProperties = (contract, log) => ({
      DEFAULT_CONTRACT_ID: contract.contract_id,
      DEFAULT_CONTRACT_TIMESTAMP: log.timestamp,
    });

    const contractParameters = getPropertiesForEntity(extractProperties, {
      entitiesKey: contractListName,
      subResourcePath: 'results/logs',
    });

    const contractResultsPath = `${baseUrlPrefix}/contracts/results`;
    const firstContractResult = getFirstEntity(contractResultsPath, resultListName);
    contractParameters['DEFAULT_CONTRACT_RESULT_HASH'] = firstContractResult.hash;
    return contractParameters;
  }
);

const computeNftParameters = wrapComputeParametersFunc(['DEFAULT_NFT_ID', 'DEFAULT_NFT_SERIAL'], () => {
  const extractProperties = (token, nft) => ({DEFAULT_NFT_ID: token.token_id, DEFAULT_NFT_SERIAL: nft.serial_number});
  return getPropertiesForEntity(extractProperties, {
    entitiesKey: tokenListName,
    queryParamMap: {type: 'NON_FUNGIBLE_UNIQUE'},
    subResourcePath: 'nfts',
  });
});

const computeScheduleParameters = wrapComputeParametersFunc(
  ['DEFAULT_SCHEDULE_ACCOUNT_ID', 'DEFAULT_SCHEDULE_ID'],
  () => {
    const extractProperties = (schedule) => ({
      DEFAULT_SCHEDULE_ACCOUNT_ID: schedule.creator_account_id,
      DEFAULT_SCHEDULE_ID: schedule.schedule_id,
    });
    return getPropertiesForEntity(extractProperties, {
      entitiesKey: scheduleListName,
      queryParamMap: {limit: 1},
    });
  }
);

const computeFungibleTokenParameters = wrapComputeParametersFunc(['DEFAULT_TOKEN_ID'], () => {
  const extractProperties = (token) => ({DEFAULT_TOKEN_ID: token.token_id});
  return getPropertiesForEntity(extractProperties, {
    entitiesKey: tokenListName,
    queryParamMap: {type: 'FUNGIBLE_COMMON', limit: 1},
  });
});

const computeTokenParameters = wrapComputeParametersFunc(['DEFAULT_TOKEN_NAME'], () => {
  const extractProperties = (token) => ({DEFAULT_TOKEN_NAME: token.name});
  return getPropertiesForEntity(extractProperties, {
    entitiesKey: tokenListName,
    queryParamMap: {limit: 1},
  });
});

const computeTransactionParameters = wrapComputeParametersFunc(
  ['DEFAULT_TRANSACTION_HASH', 'DEFAULT_TRANSACTION_ID'],
  () => {
    const extractProperties = (transaction) => ({
      DEFAULT_TRANSACTION_HASH: b64encode(b64decode(transaction.transaction_hash), 'url'),
      DEFAULT_TRANSACTION_ID: transaction.transaction_id,
    });
    return getPropertiesForEntity(extractProperties, {
      entitiesKey: transactionListName,
      queryParamMap: {limit: 1, transactiontype: 'cryptotransfer'},
    });
  }
);

const computeTopicInfo = wrapComputeParametersFunc(
  ['DEFAULT_TOPIC_ID', 'DEFAULT_TOPIC_SEQUENCE', 'DEFAULT_TOPIC_TIMESTAMP'],
  () => {
    const extractProperties = (transaction) => ({DEFAULT_TOPIC_ID: transaction.entity_id});
    const topicParameters = getPropertiesForEntity(extractProperties, {
      entitiesKey: transactionListName,
      queryParamMap: {limit: 1, result: 'success', transactiontype: 'CONSENSUSSUBMITMESSAGE'},
    });

    const topicMessagePath = `${baseUrlPrefix}/topics/${topicParameters.DEFAULT_TOPIC_ID}/messages`;
    const firstTopicMessage = getFirstEntity(topicMessagePath, messageListName);
    return Object.assign(topicParameters, {
      DEFAULT_TOPIC_SEQUENCE: firstTopicMessage.sequence_number,
      DEFAULT_TOPIC_TIMESTAMP: firstTopicMessage.consensus_timestamp,
    });
  }
);

const allHandlers = [
  computeAccountParameters,
  computeAccountWithCryptoAllowanceParameters,
  computeAccountWithNftsParameters,
  computeAccountWithTokenAllowanceParameters,
  computeAccountWithTokenParameters,
  computeBlockParameters,
  computeContractParameters,
  computeFungibleTokenParameters,
  computeNftParameters,
  computeScheduleParameters,
  computeTokenParameters,
  computeTopicInfo,
  computeTransactionParameters,
];

const setupTestParameters = (requiredParameters) => {
  const testParameters = {
    BASE_URL_PREFIX: baseUrlPrefix,
    DEFAULT_BALANCE_TIMESTAMP: __ENV['DEFAULT_BALANCE_TIMESTAMP'],
    DEFAULT_TOKEN_BALANCE_TIMESTAMP: __ENV['DEFAULT_TOKEN_BALANCE_TIMESTAMP'],
    DEFAULT_TOKEN_TIMESTAMP: __ENV['DEFAULT_TOKEN_TIMESTAMP'],
    DEFAULT_LIMIT: __ENV['DEFAULT_LIMIT'],
  };
  Object.assign(testParameters, computeTestParameters(requiredParameters, allHandlers));
  console.info(`Test parameters - ${JSON.stringify(testParameters, null, '\t')}`);
  return testParameters;
};

export {setupTestParameters};
