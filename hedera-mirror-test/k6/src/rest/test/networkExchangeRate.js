// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isSuccess, RestTestScenarioBuilder} from '../libex/common.js';

const urlTag = '/network/exchangerate';

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('networkExchangeRate') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${urlTag}`;
    return http.get(url);
  })
  .check('Network exchangerate OK', isSuccess)
  .build();

export {options, run, setup};
