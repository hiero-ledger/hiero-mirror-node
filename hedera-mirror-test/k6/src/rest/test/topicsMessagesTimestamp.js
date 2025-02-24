// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isSuccess, RestTestScenarioBuilder} from '../libex/common.js';

const urlTag = '/topics/messages/{timestamp}';

const getUrl = (testParameters) => `/topics/messages/${testParameters['DEFAULT_TOPIC_TIMESTAMP']}`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('topicsMessageTimestamp') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_TOPIC_TIMESTAMP')
  .check('Topics messages timestamp OK', isSuccess)
  .build();

export {getUrl, options, run, setup};
