// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {fromHex, isSuccess, RestJavaTestScenarioBuilder} from '../libex/common.js';

const urlTag = '/network/fees';

// Signed CryptoTransfer of 1 tinybar from 0.0.2 to 0.0.3, using each account's long-zero alias.
// Each amount follows its alias with field tag 10, giving 1001 for the sender and 1002 for the receiver.
const transactionHex =
  '2a3e0a3c723a0a38' + // CryptoTransfer transaction
  '0a1a0a162214' + // AccountAmount entry
  '0000000000000000000000000000000000000002' + // sender
  '1001' + // sender amount -> -1
  '0a1a0a162214' + // AccountAmount entry
  '0000000000000000000000000000000000000003' + // receiver
  '1002'; // receiver amount -> 1

const transaction = fromHex(transactionHex);

const {options, run, setup} = new RestJavaTestScenarioBuilder()
  .name('networkFeesEstimate') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${urlTag}`;
    return http.post(url, transaction, {headers: {'Content-Type': 'application/protobuf'}});
  })
  .check('Network fee estimate OK', isSuccess)
  .build();

export {options, run, setup};
