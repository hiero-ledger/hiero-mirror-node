// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestTestScenarioBuilder} from '../libex/common.js';
import {accountListName} from '../libex/constants.js';

const urlTag = '/accounts?account.balance=ne:{balance}&order=desc';

const getUrl = (testParameters) =>
  `/accounts?account.balance=ne:${testParameters['DEFAULT_ACCOUNT_BALANCE']}&order=desc`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('accountsBalanceNe') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_ACCOUNT_BALANCE')
  .check('Accounts balance NE OK', (r) => isValidListResponse(r, accountListName))
  .build();

export {getUrl, options, run, setup};
