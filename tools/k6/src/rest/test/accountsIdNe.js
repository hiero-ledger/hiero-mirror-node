// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestTestScenarioBuilder} from '../libex/common.js';
import {accountListName} from '../libex/constants.js';

const urlTag = '/accounts?account.id=ne:{accountId}&order=desc';

const getUrl = (testParameters) => `/accounts?account.id=ne:${testParameters['DEFAULT_ACCOUNT_ID']}&order=desc`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('accountsIdNe') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_ACCOUNT_ID')
  .check('Accounts ne: accountId order desc OK', (r) => isValidListResponse(r, accountListName))
  .build();

export {getUrl, options, run, setup};
