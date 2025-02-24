// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestJavaTestScenarioBuilder} from '../libex/common.js';
import {accountNftAllowanceListName} from '../libex/constants.js';

const urlTag = '/accounts/{id}/allowances/nfts';

const getUrl = (testParameters) =>
  `/accounts/${testParameters['DEFAULT_ACCOUNT_ID_NFTS_ALLOWANCE_OWNER']}/allowances/nfts?owner=true&limit=${testParameters['DEFAULT_LIMIT']}`;

const {options, run, setup} = new RestJavaTestScenarioBuilder()
  .name('accountsNftAllowanceOwnerResults') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_ACCOUNT_ID_NFTS_ALLOWANCE_OWNER')
  .check('Account NFT allowances owner results OK', (r) => isValidListResponse(r, accountNftAllowanceListName))
  .build();

export {getUrl, options, run, setup};
