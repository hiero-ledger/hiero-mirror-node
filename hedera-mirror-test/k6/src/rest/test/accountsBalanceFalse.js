/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import http from 'k6/http';

import {isValidListResponse, RestTestScenarioBuilder} from '../libex/common.js';
import {accountListName} from '../libex/constants.js';

const urlTag = '/accounts?balance=false';

const {options, run, setup} = new RestTestScenarioBuilder()
  .name('accountsBalanceFalse') // use unique scenario name among all tests
  .tags({url: urlTag})
  .request((testParameters) => http.get(`${testParameters['BASE_URL_PREFIX']}${urlTag}`))
  .check('Accounts balance false OK', (r) => isValidListResponse(r, accountListName))
  .build();

export {options, run, setup};
