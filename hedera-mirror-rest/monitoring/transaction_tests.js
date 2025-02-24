// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';
import * as math from 'mathjs';
import config from './config';

import {
  checkAPIResponseError,
  checkElementsOrder,
  checkMandatoryParams,
  checkResourceFreshness,
  checkRespArrayLength,
  checkRespObj,
  checkRespObjDefined,
  CheckRunner,
  DEFAULT_LIMIT,
  fetchAPIResponse,
  getUrl,
  hasEmptyList,
  testRunner,
} from './utils';

const transactionsPath = '/transactions';
const resource = 'transaction';
const resourceLimit = config[resource].limit || DEFAULT_LIMIT;
const jsonRespKey = 'transactions';
const mandatoryParams = [
  'consensus_timestamp',
  'valid_start_timestamp',
  'charged_tx_fee',
  'transaction_id',
  'memo_base64',
  'result',
  'name',
  'node',
  'transfers',
];

const mergeArrays = (...arrayList) => {
  return arrayList
    .filter((array) => array != null)
    .reduce((previous, current) => {
      previous.push(...current);
      return previous;
    }, []);
};

const checkTransactionTransfers = (transactions, option) => {
  const {accountId, message} = option;
  const transaction = transactions[0];
  const transfers = mergeArrays(transaction.transfers, transaction.token_transfers);
  if (!transfers || !transfers.some((xfer) => xfer.account === accountId)) {
    return {
      passed: false,
      message,
    };
  }

  return {passed: true};
};

/**
 * Verify base transactions call
 * Also ensure an account mentioned in the transaction can be connected with the said transaction
 * @param {String} server API host endpoint
 */
const getTransactionsWithAccountCheck = async (server) => {
  let url = getUrl(server, transactionsPath, {limit: resourceLimit, type: 'credit'});
  const transactions = await fetchAPIResponse(url, jsonRespKey);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'transactions is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: resourceLimit,
      message: (elements, limit) => `transactions.length of ${elements.length} is less than limit ${limit}`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'transaction object is missing some mandatory fields',
    })
    .run(transactions);
  if (!result.passed) {
    return {url, ...result};
  }

  const transaction = transactions[0];
  const transfers = mergeArrays(transaction.transfers, transaction.token_transfers);

  const highestAccount = _.max(
    _.map(
      _.filter(transfers, (xfer) => xfer.amount > 0),
      (xfer) => xfer.account
    )
  );

  if (highestAccount === undefined) {
    return {
      url,
      passed: false,
      message: 'no account found in transaction transfers list and token_transfers list',
    };
  }

  url = getUrl(server, transactionsPath, {
    'account.id': highestAccount,
    type: 'credit',
    limit: 1,
  });
  const accTransactions = await fetchAPIResponse(url, jsonRespKey, hasEmptyList(jsonRespKey));

  result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'transactions is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: 1,
      message: (elements) => `transactions.length of ${elements.length} is not 1`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'transaction object is missing some mandatory fields',
    })
    .withCheckSpec(checkTransactionTransfers, {
      accountId: highestAccount,
      message: 'Highest acc check was not found',
    })
    .run(accTransactions);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called transactions and performed account check',
  };
};

/**
 * Verify transactions call with order query params provided
 * @param {String} server API host endpoint
 */
const getTransactionsWithOrderParam = async (server) => {
  const url = getUrl(server, transactionsPath, {order: 'asc', limit: resourceLimit});
  const transactions = await fetchAPIResponse(url, jsonRespKey);

  const result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'transactions is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: resourceLimit,
      message: (elements, limit) => `transactions.length of ${elements.length} is less than limit ${limit}`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'transaction object is missing some mandatory fields',
    })
    .withCheckSpec(checkElementsOrder, {asc: true, key: 'consensus_timestamp', name: 'consensus timestamp'})
    .run(transactions);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called transactions with order params only',
  };
};

/**
 * Verify transactions call with limit query params provided
 * @param {Object} server API host endpoint
 */
const getTransactionsWithLimitParams = async (server) => {
  const url = getUrl(server, transactionsPath, {limit: 10});
  const transactions = await fetchAPIResponse(url, jsonRespKey);

  const result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'transactions is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: 10,
      message: (elements) => `transactions.length of ${elements.length} was expected to be 10`,
    })
    .run(transactions);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called transactions with limit params only',
  };
};

/**
 * Verify transactions call with time and limit query params provided
 * @param {Object} server API host endpoint
 */
const getTransactionsWithTimeAndLimitParams = async (server) => {
  let url = getUrl(server, transactionsPath, {limit: 1});
  let transactions = await fetchAPIResponse(url, jsonRespKey);

  const checkRunner = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'transactions is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: 1,
      message: (elements) => `transactions.length of ${elements.length} was expected to be 1`,
    });
  let result = checkRunner.run(transactions);
  if (!result.passed) {
    return {url, ...result};
  }

  const plusOne = math.add(math.bignumber(transactions[0].consensus_timestamp), math.bignumber(1));
  const minusOne = math.subtract(math.bignumber(transactions[0].consensus_timestamp), math.bignumber(1));
  url = getUrl(server, transactionsPath, {
    timestamp: [`gt:${minusOne.toString()}`, `lt:${plusOne.toString()}`],
    limit: 1,
  });
  transactions = await fetchAPIResponse(url, jsonRespKey, hasEmptyList(jsonRespKey));

  result = checkRunner.run(transactions);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called transactions with time and limit params',
  };
};

/**
 * Verify there is at least one successful transaction for the latest successful transaction
 * @param {Object} server API host endpoint
 */
const getSuccessfulTransactionById = async (server) => {
  // look for the latest successful transaction
  let url = getUrl(server, transactionsPath, {limit: 1, result: 'success'});
  const transactions = await fetchAPIResponse(url, jsonRespKey);

  const checkRunner = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'transactions is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: 1,
      message: (elements) => `transactions.length of ${elements.length} was expected to be 1`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'transaction object is missing some mandatory fields',
    });
  let result = checkRunner.run(transactions);
  if (!result.passed) {
    return {url, ...result};
  }

  // filter the scheduled transaction
  url = getUrl(server, `${transactionsPath}/${transactions[0].transaction_id}`, {scheduled: false});
  const singleTransactions = await fetchAPIResponse(url, jsonRespKey);

  // only verify the single successful transaction
  result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'transactions is undefined'})
    .withCheckSpec(checkRespObj, {
      predicate: (data) => data.filter((tx) => tx.result === 'SUCCESS').length >= 1,
      message: `Transactions array should have at least one successful transaction`,
    })
    .run(singleTransactions);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully retrieved single transactions by id',
  };
};

/**
 * Verfiy the freshness of transactions returned by the api
 * @param {Object} server API host endpoint
 */
const checkTransactionFreshness = async (server) => {
  return checkResourceFreshness(server, transactionsPath, resource, (data) => data.consensus_timestamp, jsonRespKey);
};

/**
 * Run all transaction tests in an asynchronous fashion waiting for all tests to complete
 *
 * @param {Object} server object provided by the user
 * @param {ServerTestResult} testResult shared server test result object capturing tests for given endpoint
 */
const runTests = async (server, testResult) => {
  const runTest = testRunner(server, testResult, resource);
  return Promise.all([
    runTest(getTransactionsWithAccountCheck),
    runTest(getTransactionsWithOrderParam),
    runTest(getTransactionsWithLimitParams),
    runTest(getTransactionsWithTimeAndLimitParams),
    runTest(getSuccessfulTransactionById),
    runTest(checkTransactionFreshness),
  ]);
};

export default {
  resource,
  runTests,
};
