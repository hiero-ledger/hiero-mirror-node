// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestTestScenarioBuilder} from '../libex/common.js';
import {messageListName} from '../libex/constants.js';

const urlTag = '/topics/{id}/messages';

const getUrl = (testParameters) =>
  `/topics/${testParameters['DEFAULT_TOPIC_ID']}/messages?limit=${testParameters['DEFAULT_LIMIT']}`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('topicsIdMessages') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_TOPIC_ID')
  .check('Topics id messages OK', (r) => isValidListResponse(r, messageListName))
  .build();

export {getUrl, options, run, setup};
