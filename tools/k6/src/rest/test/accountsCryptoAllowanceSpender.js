// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isSuccess, RestTestScenarioBuilder} from '../libex/common.js';

const urlTag = '/accounts/{id}/allowances/crypto';

const getUrl = (testParameters) => `/accounts/${testParameters['DEFAULT_ACCOUNT_ID']}/allowances/crypto?spender.id=98`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('accountCryptoAllowancesResultsSpender') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_ACCOUNT_ID')
  .check('Account crypto allowances for spender results OK', (r) => isSuccess(r))
  .build();

export {getUrl, options, run, setup};
