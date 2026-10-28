// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {isValidListResponse, RestJavaTestScenarioBuilder} from '../libex/common.js';
import {clprLedgerConfigurations} from '../libex/constants.js';

const urlTag = '/clpr/ledger-configuration';

const getUrl = (testParameters) => `${urlTag}?limit=${testParameters['DEFAULT_LIMIT']}`;

const {options, run, setup} = new RestJavaTestScenarioBuilder()
  .name('clprLedgerConfiguration') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => {
    const url = `${testParameters['BASE_URL_PREFIX']}${getUrl(testParameters)}`;
    return http.get(url);
  })
  .check('CLPR Ledger Configuration OK', (r) => isValidListResponse(r, clprLedgerConfigurations))
  .build();

export {getUrl, options, run, setup};
