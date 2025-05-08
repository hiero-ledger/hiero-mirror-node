// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestTestScenarioBuilder} from '../libex/common.js';
import {nftListName} from '../libex/constants.js';

const urlTag = '/accounts/{id}/nfts';

const getUrl = (testParameters) =>
  `/accounts/${testParameters['DEFAULT_ACCOUNT_ID_NFTS']}/nfts?limit=${testParameters['DEFAULT_LIMIT']}`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('accountNftsResults') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_ACCOUNT_ID_NFTS')
  .check('Account nfts results OK', (r) => isValidListResponse(r, nftListName))
  .build();

export {getUrl, options, run, setup};
