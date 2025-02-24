// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestTestScenarioBuilder} from '../libex/common.js';
import {balanceListName} from '../libex/constants.js';

const urlTag = '/balances?account.id=eq:{accountId}';

const getUrl = (testParameters) => `/balances?account.id=eq:${testParameters['DEFAULT_ACCOUNT_ID']}`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('balancesAccount') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_ACCOUNT_ID')
  .check('Balances for specific account OK', (r) => isValidListResponse(r, balanceListName))
  .build();

export {getUrl, options, run, setup};
