// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestTestScenarioBuilder} from '../libex/common.js';
import {transactionListName} from '../libex/constants.js';

const urlTag = '/transactions?transactionType={transactionType}';

const getUrl = (testParameters) =>
  `/transactions?transactionType=CRYPTOTRANSFER&limit=${testParameters['DEFAULT_LIMIT']}&order=asc`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('transactionsByTransactionType') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .check('Transactions by transaction type OK', (r) => isValidListResponse(r, transactionListName))
  .build();

export {getUrl, options, run, setup};
