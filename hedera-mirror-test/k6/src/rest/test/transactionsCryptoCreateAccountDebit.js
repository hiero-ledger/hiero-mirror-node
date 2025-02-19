/*
 * Copyright (C) 2022-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
