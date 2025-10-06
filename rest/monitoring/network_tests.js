// SPDX-License-Identifier: Apache-2.0

import config from './config';

import {
  checkAPIResponseError,
  checkMandatoryParams,
  checkRespArrayLength,
  checkRespObjDefined,
  CheckRunner,
  DEFAULT_LIMIT,
  fetchAPIResponse,
  getUrl,
  testRunner,
} from './utils';

const network = '/network';
const resource = 'network';
const jsonRespKey = '';
const jsonNodesRespKey = 'nodes';
const resourceLimit = config[resource].limit || DEFAULT_LIMIT;

/**
 * Verify network exchangeRate call
 * @param {Object} server API host endpoint
 * @returns {{url: string, passed: boolean, message: string}}
 */
const getNetworkExchangeRate = async (server) => {
  let url = getUrl(server, network + '/exchangerate');
  const networkExchangeRate = await fetchAPIResponse(url, jsonRespKey);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'network exchangerate is undefined'})
    .run(networkExchangeRate);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called networkExchangeRate',
  };
};

/**
 * Verify network fees call
 * @param {Object} server API host endpoint
 * @returns {{url: string, passed: boolean, message: string}}
 */
const getNetworkFees = async (server) => {
  let url = getUrl(server, network + '/fees');
  const jsonFeesRespKey = 'fees';
  const networkFees = await fetchAPIResponse(url, jsonFeesRespKey);
  const returnParams = ['gas', 'transaction_type'];

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'network fees is undefined'})
    .withCheckSpec(checkMandatoryParams, {
      params: returnParams,
      message: 'network fees object is missing some mandatory fields',
    })
    .run(networkFees);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called networkFees',
  };
};

/**
 * Verify network nodes call
 * @param {Object} server API host endpoint
 * @returns {{url: string, passed: boolean, message: string}}
 */
const getNetworkNodes = async (server) => {
  let url = getUrl(server, network + '/nodes', {limit: resourceLimit});
  const networkNodes = await fetchAPIResponse(url, jsonNodesRespKey);
  const returnParams = [
    'description',
    'file_id',
    'max_stake',
    'memo',
    'min_stake',
    'node_id',
    'node_account_id',
    'node_cert_hash',
    'public_key',
    'reward_rate_start',
    'service_endpoints',
    'stake',
    'stake_not_rewarded',
    'stake_rewarded',
    'staking_period',
    'timestamp',
  ];

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'network nodes is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: resourceLimit,
      message: (elements, limit) => `nodes.length of ${elements.length} is less than limit ${limit}`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: returnParams,
      message: 'network nodes object is missing some mandatory fields',
    })
    .run(networkNodes);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called networkNodes',
  };
};

/**
 * Verify network stake call
 * @param {Object} server API host endpoint
 * @returns {{url: string, passed: boolean, message: string}}
 */
const getNetworkStake = async (server) => {
  let url = getUrl(server, network + '/stake');
  const networkStake = await fetchAPIResponse(url, jsonRespKey);
  const mandatoryParams = [
    'max_stake_rewarded',
    'max_staking_reward_rate_per_hbar',
    'max_total_reward',
    'node_reward_fee_fraction',
    'reserved_staking_rewards',
    'reward_balance_threshold',
    'stake_total',
    'staking_period',
    'staking_period_duration',
    'staking_periods_stored',
    'staking_reward_fee_fraction',
    'staking_reward_rate',
    'staking_start_threshold',
    'unreserved_staking_reward_balance',
  ];

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'network stake is undefined'})
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'network stake object is missing some mandatory fields',
    })
    .run(networkStake);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called networkStake',
  };
};

/**
 * Verify network supply call
 * @param {Object} server API host endpoint
 * @returns {{url: string, passed: boolean, message: string}}
 */
const getNetworkSupply = async (server) => {
  let url = getUrl(server, network + '/supply');
  const networkSupply = await fetchAPIResponse(url, jsonRespKey);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'network supply is undefined'})
    .run(networkSupply);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called networkSupply',
  };
};

/**
 * Run all network tests in an asynchronous fashion waiting for all tests to complete
 * @param {Object} server object provided by the user
 * @param {ServerTestResult} testResult shared server test result object capturing tests for given endpoint
 */
const runTests = async (server, testResult) => {
  const runTest = testRunner(server, testResult, resource);
  return Promise.all([
    runTest(getNetworkExchangeRate),
    runTest(getNetworkFees),
    runTest(getNetworkNodes),
    runTest(getNetworkStake, 'REST_JAVA'),
    runTest(getNetworkSupply),
  ]);
};

export default {
  resource,
  runTests,
};
