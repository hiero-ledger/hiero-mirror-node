// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder} from './common.js';

const contract = __ENV.DEFAULT_CONTRACT_ADDRESS;
const account = __ENV.DEFAULT_ACCOUNT_ADDRESS;

const {options, run} = new ContractCallTestScenarioBuilder()
  .name('contractCallReceive') // use unique scenario name among all tests
  .from(account)
  .to(contract)
  .build();

export {options, run};
