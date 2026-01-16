// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestJavaTestScenarioBuilder} from '../libex/common.js';
import {accountNftAllowanceListName} from '../libex/constants.js';

const urlTag = '/accounts/{id}/allowances/nfts';

const getUrl = (testParameters) =>
  `/accounts/${testParameters['DEFAULT_ACCOUNT_ID_NFTS_ALLOWANCE_OWNER']}/allowances/nfts?owner=true&limit=${testParameters['DEFAULT_LIMIT']}`;

const {options, run, setup} = new RestJavaTestScenarioBuilder()
  .name('rampUp') // use unique scenario name among all tests
  .tags({url: urlTag})
  .scenario({
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      {
        duration: __ENV.DEFAULT_RAMPUP_DURATION || __ENV.DEFAULT_DURATION,
        target: __ENV.DEFAULT_RAMPUP_VUS || __ENV.DEFAULT_VUS,
      },
    ],
    gracefulRampDown: '0s',
  })
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .check('Account NFT allowances owner results OK', (r) => isValidListResponse(r, accountNftAllowanceListName))
  .build();

export {options, run, setup};
