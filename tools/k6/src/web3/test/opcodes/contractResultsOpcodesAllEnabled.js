// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {TestScenarioBuilder} from '../../../lib/common.js';
import {isValidListResponse} from '../common.js';

const urlTag = '/contracts/results/{transactionId}/opcodes?stack=true&memory=true&storage=true';

const baseUrl = __ENV.BASE_URL_PREFIX;
const transactionId = __ENV.TRANSACTION_ID;

const path = `/contracts/results/${transactionId}/opcodes`;
const params = {
  headers: {
    'Accept-Encoding': 'gzip',
  },
};

const {options, run} = new TestScenarioBuilder()
  .name('opcodesAllEnabled') // use unique scenario name among all tests
  .request(() => {
    const url = `${baseUrl}${path}`;
    return http.get(url, params);
  })
  .check('Opcodes list is not empty.', (r) => isValidListResponse(r, 'opcodes'))
  .build();

export {options, run};
