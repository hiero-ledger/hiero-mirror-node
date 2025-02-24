// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestJavaTestScenarioBuilder} from '../libex/common.js';
import {airdrops} from '../libex/constants.js';

const urlTag = '/accounts/{id}/airdrops/pending';

const getUrl = (testParameters) =>
  `/accounts/${testParameters['DEFAULT_ACCOUNT_ID_AIRDROP_RECEIVER']}/airdrops/pending?limit=${testParameters['DEFAULT_LIMIT']}&order=desc`;

const {options, run, setup} = new RestJavaTestScenarioBuilder()
  .name('accountsPendingAirdrop') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_ACCOUNT_ID_AIRDROP_RECEIVER')
  .check('Pending airdrop for receiver', (r) => isValidListResponse(r, airdrops))
  .build();

export {getUrl, options, run, setup};
