// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestTestScenarioBuilder} from '../libex/common.js';
import {accountListName} from '../libex/constants.js';

const urlTag = '/accounts?account.balance=gt:0&account.publickey={publicKey}';

const getUrl = (testParameters) =>
  `/accounts?account.balance=gte:0&account.publickey=${testParameters['DEFAULT_PUBLIC_KEY']}`;

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('accountsBalanceGte0Pubkey') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .requiredParameters('DEFAULT_PUBLIC_KEY')
  .check('Accounts balance gt:0 with publickey OK', (r) => isValidListResponse(r, accountListName))
  .build();

export {getUrl, options, run, setup};
