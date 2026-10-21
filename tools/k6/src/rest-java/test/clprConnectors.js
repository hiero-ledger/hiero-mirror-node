// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestJavaTestScenarioBuilder} from '../libex/common.js';
import {clprConnectors} from '../libex/constants.js';

const urlTag = '/clpr/connectors';

const getUrl = (testParameters) => `${urlTag}?limit=${testParameters['DEFAULT_LIMIT']}`;

const {options, run, setup} = new RestJavaTestScenarioBuilder()
  .name('clprConnectors') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .check('CLPR Connectors OK', (r) => isValidListResponse(r, clprConnectors))
  .build();

export {getUrl, options, run, setup};
