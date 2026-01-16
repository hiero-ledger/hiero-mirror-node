// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isSuccess, RestTestScenarioBuilder} from '../libex/common.js';

const urlTag = '/tokens/{id}/nfts/{serial}';

const getUrl = (testParameters) =>
  `/tokens/${testParameters['DEFAULT_NFT_ID']}/nfts/${testParameters['DEFAULT_NFT_SERIAL']}`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('tokensNftsSerial') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_NFT_ID', 'DEFAULT_NFT_SERIAL')
  .check('Tokens nfts serial OK', isSuccess)
  .build();

export {getUrl, options, run, setup};
