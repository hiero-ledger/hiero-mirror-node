// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestTestScenarioBuilder} from '../libex/common.js';
import {accountListName} from '../libex/constants.js';

const urlTag = '/accounts?account.balance=gt:0';

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('accountsBalanceGt0') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${urlTag}`;
    return http.get(url);
  })
  .check('Accounts balance gt:0 OK', (r) => isValidListResponse(r, accountListName))
  .build();

export {options, run, setup};
