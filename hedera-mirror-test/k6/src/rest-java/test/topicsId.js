// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isSuccess, RestJavaTestScenarioBuilder} from '../libex/common.js';

const urlTag = '/topics/{id}';

const getUrl = (testParameters) => `/topics/${testParameters['DEFAULT_TOPIC_WITH_FEE_ID']}`;

const {options, run, setup} = new RestJavaTestScenarioBuilder()
  .name('topicsId') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_TOPIC_WITH_FEE_ID')
  .check('Topics ID OK', isSuccess)
  .build();

export {getUrl, options, run, setup};
