// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isSuccess, RestTestScenarioBuilder} from '../libex/common.js';

const urlTag = '/transactions/{id}';

const getUrl = (testParameters) => `/transactions/${testParameters['DEFAULT_TRANSACTION_ID']}`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('transactionsId') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_TRANSACTION_ID')
  .check('Transactions id OK', isSuccess)
  .build();

export {getUrl, options, run, setup};
