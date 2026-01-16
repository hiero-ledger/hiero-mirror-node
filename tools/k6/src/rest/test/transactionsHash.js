// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isSuccess, RestTestScenarioBuilder} from '../libex/common.js';

const urlTag = '/transactions/{hash}';

const getUrl = (testParameters) => `/transactions/${testParameters['DEFAULT_TRANSACTION_HASH']}`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('transactionsHash') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_TRANSACTION_HASH')
  .check('Transactions hash OK', isSuccess)
  .build();

export {getUrl, options, run, setup};
