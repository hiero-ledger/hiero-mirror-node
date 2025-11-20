// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder, getMixedBlocks} from './common.js';

const contract = __ENV.HTS_CONTRACT_ADDRESS;
const selector = '0xf429f19b';
const token = __ENV.TOKEN_ADDRESS;

const {options, run} = new ContractCallTestScenarioBuilder()
  .name('contractCallTokenType') // use unique scenario name among all tests
  .selector(selector)
  .args([token])
  .to(contract)
  .blocks(getMixedBlocks())
  .build();

export {options, run};
