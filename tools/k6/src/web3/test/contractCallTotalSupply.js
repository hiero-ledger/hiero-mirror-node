// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder, getMixedBlocks} from './common.js';

const contract = __ENV.ERC_CONTRACT_ADDRESS;
const selector = '0xe4dc2aa4';
const token = __ENV.TOKEN_ADDRESS;

const {options, run} = new ContractCallTestScenarioBuilder()
  .name('contractCallTotalSupply') // use unique scenario name among all tests
  .selector(selector)
  .args([token])
  .to(contract)
  .blocks(getMixedBlocks())
  .build();

export {options, run};
