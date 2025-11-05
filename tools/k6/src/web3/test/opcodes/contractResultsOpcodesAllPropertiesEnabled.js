// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {MultiIdScenarioBuilder} from '../../../lib/common.js';
import {isValidListResponse} from '../common.js';
import {SharedArray} from 'k6/data';
import {check} from 'k6';

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
  .check('Opcodes list is not empty.', (r) => isValidListResponse(r, 'opcodes', 4))
  .build();

export {options, run};
