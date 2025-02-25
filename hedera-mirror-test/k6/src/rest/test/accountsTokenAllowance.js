// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestTestScenarioBuilder} from '../libex/common.js';
import {allowanceListName} from '../libex/constants.js';

const urlTag = '/accounts/{id}/allowances/tokens';

const getUrl = (testParameters) =>
  `/accounts/${testParameters['DEFAULT_ACCOUNT_ID_TOKEN_ALLOWANCE']}/allowances/tokens?limit=${testParameters['DEFAULT_LIMIT']}`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('accountTokenAllowancesResults') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_ACCOUNT_ID_TOKEN_ALLOWANCE')
  .check('Account token allowances results OK', (r) => isValidListResponse(r, allowanceListName))
  .build();

export {getUrl, options, run, setup};
