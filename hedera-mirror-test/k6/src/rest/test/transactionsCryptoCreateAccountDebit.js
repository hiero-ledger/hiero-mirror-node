// SPDX-License-Identifier: Apache-2.0

/**
 * This is a very particular test case related to issue #2385.
 * While testing performance issues, it was found that calls using both the transaction type (e.g. CRYPTOCREATEACCOUNT)
 * and balance modification type (e.g. debit) query string parameters, performance was especially slow. API calls would timeout after 20 seconds.
 * This test uses hard-coded transaction type and balance modification type values because slow performance seems to be
 * associated with a less frequently used transaction type.
 * An attempt to make this test more generic seems to have low-value while also making variable names confusing.
 */

import http from 'k6/http';

import {isValidListResponse, RestTestScenarioBuilder} from '../libex/common.js';
import {transactionListName} from '../libex/constants.js';

const urlTag = '/transactions?transactionType=CRYPTOCREATEACCOUNT&type=debit';

const getUrl = (testParameters) =>
  `/transactions?transactiontype=CRYPTOCREATEACCOUNT&type=debit&order=asc&limit=${testParameters['DEFAULT_LIMIT']}`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('transactionsCryptoCreateAccountDebit') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .check('Transactions of type CRYPTOCREATEACCOUNT and debit balance modification type OK', (r) =>
    isValidListResponse(r, transactionListName)
  )
  .build();

export {getUrl, options, run, setup};
