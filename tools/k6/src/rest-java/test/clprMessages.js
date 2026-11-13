// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isSuccess, RestJavaTestScenarioBuilder} from '../libex/common.js';
import {messages} from '../libex/constants.js';

const urlTag = '/clpr/channels/{channelId}/messages';

// Unlike isValidListResponse, an empty list is valid since a channel may not have any messages yet
const isValidMessagesResponse = (response) => isSuccess(response) && Array.isArray(JSON.parse(response.body)[messages]);

// The timestamp is required, so query the messages up to now
const getUrl = (testParameters) =>
  `/clpr/channels/${testParameters['DEFAULT_CLPR_CHANNEL_ID']}/messages?limit=${
    testParameters['DEFAULT_LIMIT']
  }&order=desc&timestamp=lte:${Math.floor(Date.now() / 1000)}`;

const {options, run, setup} = new RestJavaTestScenarioBuilder()
  .name('clprMessages') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_CLPR_CHANNEL_ID')
  .check('CLPR Messages OK', isValidMessagesResponse)
  .build();

export {getUrl, options, run, setup};
