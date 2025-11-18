// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder, getMixedBlocks} from './common.js';

const contract = __ENV.DEFAULT_CONTRACT_ADDRESS;
const data = '0x8070450f';

const {options, run} = new ContractCallTestScenarioBuilder()
  .name('contractCallMultiply') // use unique scenario name among all tests
  .data(data)
  .to(contract)
  .blocks(getMixedBlocks())
  .build();

export {options, run};
