// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {cryptoTransferTransaction} from '../libex/cryptoTransfer.js';
import {isSuccess, RestJavaTestScenarioBuilder} from '../libex/common.js';

const urlTag = '/network/fees';

const {options, run, setup} = new RestJavaTestScenarioBuilder()
  .name('networkFeesEstimate') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${urlTag}`;
    const transaction = cryptoTransferTransaction(
      testParameters['DEFAULT_ACCOUNT_ID_AIRDROP_SENDER'],
      testParameters['DEFAULT_ACCOUNT_ID_AIRDROP_RECEIVER']
    );
    return http.post(url, transaction, {headers: {'Content-Type': 'application/protobuf'}});
  })
  .requiredParameters('DEFAULT_ACCOUNT_ID_AIRDROP_SENDER', 'DEFAULT_ACCOUNT_ID_AIRDROP_RECEIVER')
  .check('Network fee estimate OK', isSuccess)
  .build();

export {options, run, setup};
