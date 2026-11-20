// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isSuccess, RestJavaTestScenarioBuilder} from '../libex/common.js';
import {channels} from '../libex/constants.js';

const urlTag = '/clpr/channels';

// Unlike isValidListResponse, an empty list is valid since a network may not have any channels yet
const isValidChannelsResponse = (response) => isSuccess(response) && Array.isArray(JSON.parse(response.body)[channels]);

const getUrl = (testParameters) => `${urlTag}?limit=${testParameters['DEFAULT_LIMIT']}&order=desc`;

const {options, run, setup} = new RestJavaTestScenarioBuilder()
  .name('clprChannels') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .check('CLPR Channels OK', isValidChannelsResponse)
  .build();

export {getUrl, options, run, setup};
