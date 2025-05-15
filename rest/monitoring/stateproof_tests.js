// SPDX-License-Identifier: Apache-2.0

import {
  checkAPIResponseError,
  checkMandatoryParams,
  checkRespArrayLength,
  checkRespObjDefined,
  CheckRunner,
  fetchAPIResponse,
  getUrl,
  testRunner,
} from './utils';

const resource = 'stateproof';
const transactionsPath = '/transactions';
const transactionsJsonKey = 'transactions';
const mandatoryParams = ['record_file', 'address_books', 'signature_files'];

const stateproofPath = (transactionId) => `${transactionsPath}/${transactionId}/stateproof`;

/**
 * Checks state proof for the latest successful transaction.
 *
 * @param {String} server
 * @return {{url: String, passed: boolean, message: String}}
 */
const checkStateproofForValidTransaction = async (server) => {
  let url = getUrl(server, transactionsPath, {limit: 1, order: 'desc', result: 'success'});
  const transactions = await fetchAPIResponse(url, transactionsJsonKey);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'transactions is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: 1,
      message: (elements) => `transactions.length of ${elements.length} is not 1`,
    })
    .run(transactions);
  if (!result.passed) {
    return {url, ...result};
  }

  const {transaction_id: transactionId, nonce, scheduled} = transactions[0];
  url = getUrl(server, stateproofPath(transactionId), {nonce, scheduled});
  const stateproof = await fetchAPIResponse(url);

  result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'stateproof is undefined'})
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'stateproof object is missing some mandatory fields',
    })
    .run(stateproof);

  return {
    url,
    passed: result.passed,
    message: result.message ?? `Successfully called stateproof for the latest successful transaction ${transactionId}`,
  };
};

/**
 * Run all stateproof tests in an asynchronous fashion waiting for all tests to complete
 *
 * @param {Object} server object provided by the user
 * @param {ServerTestResult} testResult shared server test result object capturing tests for given endpoint
 */
const runTests = async (server, testResult) => {
  const runTest = testRunner(server, testResult, resource);
  return runTest(checkStateproofForValidTransaction);
};

export default {
  resource,
  runTests,
};
