// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isSuccess, RestTestScenarioBuilder} from '../libex/common.js';

const urlTag = '/accounts/{accountId}';

const getUrl = (testParameters) => `/accounts/${testParameters['DEFAULT_ACCOUNT_ID']}`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('accountsId') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_ACCOUNT_ID')
  .check('Accounts Id OK', isSuccess)
  .build();

export {getUrl, options, run, setup};
