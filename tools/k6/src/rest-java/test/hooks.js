// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestJavaTestScenarioBuilder} from '../libex/common.js';
import {hooks} from '../libex/constants.js';

const urlTag = '/accounts/{ownerId}/hooks';

const getUrl = (testParameters) =>
  `/accounts/${testParameters['DEFAULT_OWNER_ID']}/hooks?limit=${testParameters['DEFAULT_LIMIT']}&order=desc`;

const {options, run, setup} = new RestJavaTestScenarioBuilder()
  .name('hooksResult') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_OWNER_ID')
  .check('Get hooks results OK', (r) => isValidListResponse(r, hooks))
  .build();

export {getUrl, options, run, setup};
