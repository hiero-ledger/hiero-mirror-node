// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestTestScenarioBuilder} from '../libex/common.js';
import {balanceListName} from '../libex/constants.js';

const urlTag = '/balances?timestamp=X';

const getUrl = (testParameters) =>
  `/balances?timestamp=${testParameters['DEFAULT_BALANCE_TIMESTAMP']}&limit=${testParameters['DEFAULT_LIMIT']}`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('balancesTimestamp') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .check('Balances with timestamp OK', (r) => isValidListResponse(r, balanceListName))
  .build();

export {getUrl, options, run, setup};
