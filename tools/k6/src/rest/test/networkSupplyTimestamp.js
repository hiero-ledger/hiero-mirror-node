// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isSuccess, RestTestScenarioBuilder} from '../libex/common.js';

const urlTag = '/network/supply?timestamp={timestamp}';

const getUrl = (testParameters) => `/network/supply?timestamp=${testParameters['DEFAULT_BALANCE_TIMESTAMP']}`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('networkSupplyTimestamp') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_BALANCE_TIMESTAMP')
  .check('Network supply OK', isSuccess)
  .build();

export {getUrl, options, run, setup};
