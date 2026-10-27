// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestJavaTestScenarioBuilder} from '../libex/common.js';
import {clprPendingChannels} from '../libex/constants.js';

const urlTag = '/clpr/channels/pending';

const getUrl = (testParameters) => `${urlTag}?limit=${testParameters['DEFAULT_LIMIT']}`;

const {options, run, setup} = new RestJavaTestScenarioBuilder()
  .name('clprPendingChannels') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .check('CLPR Pending Channels OK', (r) => isValidListResponse(r, clprPendingChannels))
  .build();

export {getUrl, options, run, setup};
