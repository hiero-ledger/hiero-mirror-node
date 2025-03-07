// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestTestScenarioBuilder} from '../libex/common.js';
import {accountListName} from '../libex/constants.js';

const urlTag = '/accounts?balance=false';

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('accountsBalanceFalse') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => http.get(`${testParameters['BASE_URL_PREFIX']}${urlTag}`))
  .check('Accounts balance false OK', (r) => isValidListResponse(r, accountListName))
  .build();

export {options, run, setup};
