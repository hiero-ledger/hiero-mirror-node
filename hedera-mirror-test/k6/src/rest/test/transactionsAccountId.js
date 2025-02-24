// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestTestScenarioBuilder} from '../libex/common.js';
import {transactionListName} from '../libex/constants.js';

const urlTag = '/transactions?account.id={accountId}';

const getUrl = (testParameters) =>
  `/transactions?account.id=${testParameters['DEFAULT_ACCOUNT_ID']}&limit=${testParameters['DEFAULT_LIMIT']}`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('transactionsAccountId') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_ACCOUNT_ID')
  .check('Transactions by account id OK', (r) => isValidListResponse(r, transactionListName))
  .build();

export {getUrl, options, run, setup};
