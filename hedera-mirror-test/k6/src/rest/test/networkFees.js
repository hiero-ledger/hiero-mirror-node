// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestTestScenarioBuilder} from '../libex/common.js';
import {feesListName} from '../libex/constants.js';

const urlTag = '/network/fees';

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('networkFees') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${urlTag}`;
    return http.get(url);
  })
  .check('Network Fees OK', (r) => isValidListResponse(r, feesListName))
  .build();

export {options, run, setup};
