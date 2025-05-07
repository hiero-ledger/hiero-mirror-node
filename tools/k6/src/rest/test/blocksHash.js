// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isSuccess, RestTestScenarioBuilder} from '../libex/common.js';

const urlTag = '/blocks/{hash}';

const getUrl = (testParameters) => `/blocks/${testParameters['DEFAULT_BLOCK_HASH']}`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('blockHash') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_BLOCK_HASH')
  .check('Blocks hash OK', isSuccess)
  .build();

export {getUrl, options, run, setup};
