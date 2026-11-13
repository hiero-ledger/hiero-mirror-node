// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isSuccess, RestJavaTestScenarioBuilder} from '../libex/common.js';

// A 404 is valid since a network may not have a CLPR singleton yet
const isSuccessOrNotFound = (response) => isSuccess(response) || response.status === 404;

const urlTag = '/clpr/endpoint-manifest';

const {options, run, setup} = new RestJavaTestScenarioBuilder()
  .name('clprEndpointManifest') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${urlTag}`;
    return http.get(url);
  })
  .check('CLPR endpoint manifest OK', isSuccessOrNotFound)
  .build();

export {options, run, setup};
