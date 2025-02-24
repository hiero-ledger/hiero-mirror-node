// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestTestScenarioBuilder} from '../libex/common.js';
import {nftListName} from '../libex/constants.js';

const urlTag = '/tokens/{id}/nfts';

const getUrl = (testParameters) => `/tokens/${testParameters['DEFAULT_NFT_ID']}/nfts`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('tokensNfts') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_NFT_ID')
  .check('Tokens nfts OK', (r) => isValidListResponse(r, nftListName))
  .build();

export {getUrl, options, run, setup};
