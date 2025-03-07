// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isSuccess, RestTestScenarioBuilder} from '../libex/common.js';

const urlTag = '/tokens/{id}?timestamp=lte:{timestamp}';

const getUrl = (testParameters) =>
  `/tokens/${testParameters['DEFAULT_TOKEN_ID']}?timestamp=lte:${testParameters['DEFAULT_TOKEN_TIMESTAMP']}`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('tokensIdTimestampLte')
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_TOKEN_ID')
  .check('Tokens id timestamp LTE OK', isSuccess)
  .build();

export {getUrl, options, run, setup};
