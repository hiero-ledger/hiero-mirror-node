// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';
import config from './config';

import {
  checkAPIResponseError,
  checkMandatoryParams,
  checkResourceFreshness,
  checkRespArrayLength,
  checkRespObjDefined,
  CheckRunner,
  DEFAULT_LIMIT,
  fetchAPIResponse,
  getUrl,
  testRunner,
} from './utils';

const blocksPath = '/blocks';
const resource = 'block';
const resourceLimit = config[resource].limit || DEFAULT_LIMIT;
const jsonRespKey = 'blocks';
const mandatoryParams = [
  'count',
  'gas_used',
  'hapi_version',
  'hash',
  'logs_bloom',
  'name',
  'number',
  'previous_hash',
  'size',
  'timestamp.from',
  'timestamp.to',
];

/**
 * Verify single block can be retrieved
 * @param {String} server API host endpoint
 */
const getSingleBlockById = async (server) => {
  let url = getUrl(server, blocksPath, {limit: 10});
  const blocks = await fetchAPIResponse(url, jsonRespKey);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'blocks is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: 10,
      message: (elements) => `blocks.length of ${elements.length} was expected to be 10`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'block object is missing some mandatory fields',
    })
    .run(blocks);
  if (!result.passed) {
    return {url, ...result};
  }

  const highestBlock = _.max(_.map(blocks, (block) => block.number));
  url = getUrl(server, `${blocksPath}/${highestBlock}`);
  const singleBlock = await fetchAPIResponse(url);

  result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'single block return object is undefined'})
    .run(singleBlock);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called blocks for single block',
  };
};

/**
 * Verify the freshness of blocks returned by the api
 * @param {String} server API host endpoint
 */
const checkBlockFreshness = async (server) => {
  return checkResourceFreshness(server, blocksPath, resource, (data) => data.timestamp.to, jsonRespKey);
};

/**
 * Run all block tests in an asynchronous fashion waiting for all tests to complete
 *
 * @param {Object} server object provided by the user
 * @param {ServerTestResult} testResult shared server test result object capturing tests for given endpoint
 */
const runTests = async (server, testResult) => {
  const runTest = testRunner(server, testResult, resource);
  return Promise.all([runTest(getSingleBlockById), runTest(checkBlockFreshness)]);
};

export default {
  resource,
  runTests,
};
