// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isSuccess, RestTestScenarioBuilder} from '../libex/common.js';

const urlTag = '/blocks/{number}';

const getUrl = (testParameters) => `/blocks/${testParameters['DEFAULT_BLOCK_NUMBER']}`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('blockNumber') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_BLOCK_NUMBER')
  .check('Blocks number OK', isSuccess)
  .build();

export {getUrl, options, run, setup};
