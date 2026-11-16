// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {MultiIdScenarioBuilder} from '../../../lib/common.js';
import {isValidListResponse} from '../common.js';
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

const {options, run} = new MultiIdScenarioBuilder(transactionIds, 'WEB3')
  .name('prestateAllDisabled')
  .url(`${baseUrl}/contracts/results/{id}/prestate?diff=false&code=false&storage=false`)
  .request((url) => http.get(url, params))
  .check('Prestate response is valid.', (r) => {
    try {
      if (!isValidListResponse(r, 'pre', 0)) {
        return false;
      }
      const post = r.json('post');
      return Array.isArray(post) && post.length === 0;
    } catch (e) {
      return false;
    }
  })
  .build();

export {options, run};
