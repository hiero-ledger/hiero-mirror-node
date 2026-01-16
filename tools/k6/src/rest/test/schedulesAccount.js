// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestTestScenarioBuilder} from '../libex/common.js';
import {scheduleListName} from '../libex/constants.js';

const urlTag = '/schedules?account.id=gte:{accountId}';

const getUrl = (testParameters) => `/schedules?account.id=gte:${testParameters['DEFAULT_SCHEDULE_ACCOUNT_ID']}`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('schedulesAccount') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_SCHEDULE_ACCOUNT_ID')
  .check('Schedules account OK', (r) => isValidListResponse(r, scheduleListName))
  .build();

export {getUrl, options, run, setup};
