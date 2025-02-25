// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder} from './common.js';

const contract = __ENV.HTS_CONTRACT_ADDRESS;
const selector = '0x1de8edad';
const token = __ENV.TOKEN_ADDRESS;

const {options, run} = new ContractCallTestScenarioBuilder()
  .name('contractCallTokenExpiryInfo') // use unique scenario name among all tests
  .selector(selector)
  .args([token])
  .to(contract)
  .build();

export {options, run};
