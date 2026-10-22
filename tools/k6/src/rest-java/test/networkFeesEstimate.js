// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isSuccess, RestJavaTestScenarioBuilder} from '../libex/common.js';

const urlTag = '/network/fees';

// Signed CryptoTransfer of 1 tinybar from 0.0.2 to 0.0.3.
const transaction = new Uint8Array([
  0x2a, 0x16, 0x0a, 0x14, 0x72, 0x12, 0x0a, 0x10, 0x0a, 0x06, 0x0a, 0x02, 0x18, 0x02, 0x10, 0x01, 0x0a, 0x06, 0x0a,
  0x02, 0x18, 0x03, 0x10, 0x02,
]).buffer;

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
