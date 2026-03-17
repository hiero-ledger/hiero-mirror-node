// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {MultiIdScenarioBuilder} from '../../../lib/common.js';
import {isNonErrorResponse} from '../common.js';
import {SharedArray} from 'k6/data';

const baseUrl = __ENV.BASE_URL_PREFIX;
const transactionIds = new SharedArray('target IDs', function () {
  const envString = __ENV.TRANSACTION_IDS || '';
  return envString
    .split(',')
    .map((s) => s.trim())
    .filter((s) => s.length > 0);
});

const params = {
  headers: {
    'Accept-Encoding': 'gzip',
  },
};

const {options, run} = new MultiIdScenarioBuilder(transactionIds)
  .name('opcodesAllEnabled')
  .url(`${baseUrl}/contracts/results/{id}/opcodes?stack=true&memory=true&storage=true`)
  .request((url) => http.get(url, params))
  .check('Response code OK.', (r) => isNonErrorResponse(r))
  .build();

export {options, run};
