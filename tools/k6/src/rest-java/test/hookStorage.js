// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestJavaTestScenarioBuilder} from '../libex/common.js';
import {hookStorage} from '../libex/constants.js';

const urlTag = '/accounts/{ownerId}/hooks/{hookId}/storage';

const getUrl = (testParameters) =>
  `/accounts/${testParameters['DEFAULT_OWNER_ID']}/hooks/${testParameters['DEFAULT_HOOK_ID']}/storage?limit=${testParameters['DEFAULT_LIMIT']}&order=asc`;

const {options, run, setup} = new RestJavaTestScenarioBuilder()
  .name('hookStorageResults') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_OWNER_ID', 'DEFAULT_HOOK_ID')
  .check('Get hook storage results OK', (r) => isValidListResponse(r, hookStorage))
  .build();

export {getUrl, options, run, setup};
