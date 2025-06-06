// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestTestScenarioBuilder} from '../libex/common.js';
import {balanceListName} from '../libex/constants.js';

const urlTag = '/balances?account.publickey={accountId}&timestamp={timestamp}';

const getUrl = (testParameters) =>
  `/balances?account.publickey=${testParameters['DEFAULT_PUBLIC_KEY']}&timestamp=${testParameters['DEFAULT_BALANCE_TIMESTAMP']}`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('balancesPublicKeyTimestamp') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_PUBLIC_KEY', 'DEFAULT_BALANCE_TIMESTAMP')
  .check('Balances OK', (r) => isValidListResponse(r, balanceListName))
  .build();

export {getUrl, options, run, setup};
