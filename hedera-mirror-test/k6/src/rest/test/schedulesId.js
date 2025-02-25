// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isSuccess, RestTestScenarioBuilder} from '../libex/common.js';

const urlTag = '/schedules/{id}';

const getUrl = (testParameters) => `/schedules/${testParameters['DEFAULT_SCHEDULE_ID']}`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('schedulesId') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_SCHEDULE_ID')
  .check('Schedules id OK', isSuccess)
  .build();

export {getUrl, options, run, setup};
