// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder} from './common.js';

const contract = __ENV.ERC_CONTRACT_ADDRESS;
const selector = '0x098f2366';
const token = __ENV.NON_FUNGIBLE_TOKEN_ADDRESS;
const serialNumber = __ENV.SERIAL_NUMBER;

console.log('ERC contract: ' + contract);
console.log('NFT: ' + token);

const {options, run} = new ContractCallTestScenarioBuilder()
  .name('contractCallApproved') // use unique scenario name among all tests
  .selector(selector)
  .args([token, serialNumber])
  .to(contract)
  .build();

export {options, run};
