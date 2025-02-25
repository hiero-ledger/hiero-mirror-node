// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestTestScenarioBuilder} from '../libex/common.js';
import {balanceListName} from '../libex/constants.js';

const urlTag = '/tokens/{id}/balances?timestamp={timestamp}';

const getUrl = (testParameters) =>
  `/tokens/${testParameters['DEFAULT_TOKEN_ID']}/balances?timestamp=${testParameters['DEFAULT_TOKEN_BALANCE_TIMESTAMP']}`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('tokensIdBalancesTimestamp') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_TOKEN_ID')
  .check('Tokens id balances timestamp OK', (r) => isValidListResponse(r, balanceListName))
  .build();

export {getUrl, options, run, setup};
