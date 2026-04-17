// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestTestScenarioBuilder} from '../libex/common.js';
import {transactionListName} from '../libex/constants.js';

const urlTag = '/transactions';

const getUrl = (testParameters) =>
  `${urlTag}?timestamp=lte:${testParameters['DEFAULT_MAX_BLOCK_TIMESTAMP']}&limit=${testParameters['DEFAULT_LIMIT']}`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('transactions') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_MAX_BLOCK_TIMESTAMP')
  .check('Transactions OK', (r) => isValidListResponse(r, transactionListName))
  .build();

export {getUrl, options, run, setup};
