// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder} from './common.js';

const contract = __ENV.ERC_CONTRACT_ADDRESS;
const selector = '0xd449a832';
const token = __ENV.TOKEN_ADDRESS;

console.log('Fungible token: ' + token);

const {options, run} = new ContractCallTestScenarioBuilder()
  .name('contractCallDecimals') // use unique scenario name among all tests
  .selector(selector)
  .args([token])
  .to(contract)
  .build();

export {options, run};
