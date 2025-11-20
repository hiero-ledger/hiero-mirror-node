// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder, getMixedBlocks} from './common.js';

const contract = __ENV.HTS_CONTRACT_ADDRESS;
const selector = '0x565ca6fa';
const token = __ENV.TOKEN_ADDRESS;
const account = __ENV.ACCOUNT_ADDRESS;

const {options, run} = new ContractCallTestScenarioBuilder()
  .name('contractCallIsFrozen') // use unique scenario name among all tests
  .selector(selector)
  .args([token, account])
  .to(contract)
  .blocks(getMixedBlocks())
  .build();

export {options, run};
