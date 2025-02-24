// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder} from './common.js';

const contract = __ENV.ERC_CONTRACT_ADDRESS;
const selector = '0x098f2366';
const token = __ENV.TOKEN_ADDRESS;
const account = __ENV.ACCOUNT_ADDRESS;

const {options, run} = new ContractCallTestScenarioBuilder()
  .name('contractCallApproved') // use unique scenario name among all tests
  .selector(selector)
  .args([token, account])
  .to(contract)
  .build();

export {options, run};
