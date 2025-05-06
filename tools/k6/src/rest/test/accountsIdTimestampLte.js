// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isSuccess, RestTestScenarioBuilder} from '../libex/common.js';

const urlTag = '/accounts/{accountId}?timestamp=lte:{timestamp}';

const getUrl = (testParameters) =>
  `/accounts/${testParameters['DEFAULT_ACCOUNT_ID']}?timestamp=lte:${testParameters['DEFAULT_ACCOUNT_ID_TIMESTAMP']}&limit=${testParameters['DEFAULT_LIMIT']}`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('accountsIdTimestampLte') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_ACCOUNT_ID', 'DEFAULT_ACCOUNT_ID_TIMESTAMP')
  .check('Accounts Id LTE OK', isSuccess)
  .build();

export {getUrl, options, run, setup};
