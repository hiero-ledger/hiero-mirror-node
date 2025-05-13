// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestJavaTestScenarioBuilder} from '../libex/common.js';
import {airdrops} from '../libex/constants.js';

const urlTag = '/accounts/{id}/airdrops/outstanding';

const getUrl = (testParameters) =>
  `/accounts/${testParameters['DEFAULT_ACCOUNT_ID_AIRDROP_SENDER']}/airdrops/outstanding?limit=${testParameters['DEFAULT_LIMIT']}&order=desc`;

const {options, run, setup} = new RestJavaTestScenarioBuilder()
  .name('accountsOutstandingAirdrop') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_ACCOUNT_ID_AIRDROP_SENDER')
  .check('Outstanding airdrop for sender', (r) => isValidListResponse(r, airdrops))
  .build();

export {getUrl, options, run, setup};
