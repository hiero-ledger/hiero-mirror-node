// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isSuccess, RestJavaTestScenarioBuilder} from '../libex/common.js';

const urlTag = '/clpr/endpoint-manifest';

const {options, run, setup} = new RestJavaTestScenarioBuilder()
  .name('clprEndpointManifest') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${urlTag}`;
    return http.get(url);
  })
  .check('CLPR Endpoint Manifest OK', isSuccess)
  .build();

export {options, run, setup};
