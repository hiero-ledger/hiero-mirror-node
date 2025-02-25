// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';
import config from './config';

import {
  checkAPIResponseError,
  checkRespArrayLength,
  checkRespObjDefined,
  CheckRunner,
  DEFAULT_LIMIT,
  fetchAPIResponse,
  getUrl,
  testRunner,
} from './utils';

const schedulesPath = '/schedules';
const resource = 'schedule';
const resourceLimit = config[resource].limit || DEFAULT_LIMIT;
const jsonRespKey = 'schedules';

/**
 * Verify /schedules and /schedule/{scheduleId} can be retrieved
 * @param {String} server API host endpoint
 */
const getScheduleById = async (server) => {
  let url = getUrl(server, schedulesPath, {limit: resourceLimit});
  const schedules = await fetchAPIResponse(url, jsonRespKey);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'schedules is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: resourceLimit,
      message: (elements) => `schedules.length of ${elements.length} was expected to be ${resourceLimit}`,
    })
    .run(schedules);
  if (!result.passed) {
    return {url, ...result};
  }

  const scheduleId = _.max(_.map(schedules, (schedule) => schedule.schedule_id));
  url = getUrl(server, `${schedulesPath}/${scheduleId}`);
  const schedule = await fetchAPIResponse(url);

  result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'schedule return object is undefined'})
    .run(schedule);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called schedules for particular scheduleId',
  };
};

/**
 * Run all schedule tests in an asynchronous fashion waiting for all tests to complete
 *
 * @param {Object} server object provided by the user
 * @param {ServerTestResult} testResult shared server test result object capturing tests for given endpoint
 */
const runTests = async (server, testResult) => {
  const runTest = testRunner(server, testResult, resource);
  return Promise.all([runTest(getScheduleById)]);
};

export default {
  resource,
  runTests,
};
