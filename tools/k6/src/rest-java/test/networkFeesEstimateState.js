// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {fromHex, isSuccess, RestJavaTestScenarioBuilder} from '../libex/common.js';

const urlTag = '/network/fees?mode=STATE';

const transactionHex = __ENV.DEFAULT_NETWORK_FEES_TRANSACTION;
const transaction = transactionHex ? fromHex(transactionHex) : undefined;

const {options, run, setup} = new RestJavaTestScenarioBuilder()
  .name('networkFeesEstimateState') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${urlTag}`;
    return http.post(url, transaction, {headers: {'Content-Type': 'application/protobuf'}});
  })
  .requiredParameters('DEFAULT_NETWORK_FEES_TRANSACTION')
  .check('Network fee estimate state OK', isSuccess)
  .build();

export {options, run, setup};
