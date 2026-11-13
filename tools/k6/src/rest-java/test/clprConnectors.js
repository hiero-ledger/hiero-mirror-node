// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isSuccess, RestJavaTestScenarioBuilder} from '../libex/common.js';
import {connectors} from '../libex/constants.js';

const urlTag = '/clpr/channels/{channelId}/connectors';

// Unlike isValidListResponse, an empty list is valid since a channel may not exist or have any connectors yet
const isValidConnectorsResponse = (response) =>
  isSuccess(response) && Array.isArray(JSON.parse(response.body)[connectors]);

const getUrl = (testParameters) =>
  `/clpr/channels/${testParameters['DEFAULT_CLPR_CHANNEL_ID']}/connectors?limit=${testParameters['DEFAULT_LIMIT']}&order=desc`;

const {options, run, setup} = new RestJavaTestScenarioBuilder()
  .name('clprConnectors') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .check('CLPR Connectors OK', isValidConnectorsResponse)
  .build();

export {getUrl, options, run, setup};
