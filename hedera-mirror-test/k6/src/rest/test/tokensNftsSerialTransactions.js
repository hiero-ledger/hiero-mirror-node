// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestTestScenarioBuilder} from '../libex/common.js';
import {transactionListName} from '../libex/constants.js';

const urlTag = '/tokens/{id}/nfts/{serial}/transactions';

const getUrl = (testParameters) =>
  `/tokens/${testParameters['DEFAULT_NFT_ID']}/nfts/${testParameters['DEFAULT_NFT_SERIAL']}/transactions`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('tokensNftsSerialTransactions') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_NFT_ID', 'DEFAULT_NFT_SERIAL')
  .check('Tokens nfts serial transactions OK', (r) => isValidListResponse(r, transactionListName))
  .build();

export {getUrl, options, run, setup};
