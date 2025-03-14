// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestTestScenarioBuilder} from '../libex/common.js';
import {tokenListName} from '../libex/constants.js';

const urlTag = '/tokens';

const getUrl = (testParameters) =>
  `${urlTag}?limit=${testParameters['DEFAULT_LIMIT']}&name=${testParameters['DEFAULT_TOKEN_NAME']}`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('tokensName') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_TOKEN_NAME')
  .check('Tokens Name OK', (r) => isValidListResponse(r, tokenListName))
  .build();

export {getUrl, options, run, setup};
