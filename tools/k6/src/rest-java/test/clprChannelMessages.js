// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestJavaTestScenarioBuilder} from '../libex/common.js';
import {clprMessages} from '../libex/constants.js';

const urlTag = '/clpr/channels/{channelId}/messages';

const getUrl = (testParameters) =>
  `/clpr/channels/${testParameters['DEFAULT_CLPR_CHANNEL_ID']}/messages?limit=${testParameters['DEFAULT_LIMIT']}`;

const {options, run, setup} = new RestJavaTestScenarioBuilder()
  .name('clprChannelMessages') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_CLPR_CHANNEL_ID')
  .check('CLPR Channel Messages OK', (r) => isValidListResponse(r, clprMessages))
  .build();

export {getUrl, options, run, setup};
