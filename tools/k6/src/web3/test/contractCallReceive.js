// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder, getMixedBlocks} from './common.js';

const contract = __ENV.DEFAULT_CONTRACT_ADDRESS;
const account = __ENV.PAYER_ACCOUNT;

const {options, run} = new ContractCallTestScenarioBuilder()
  .name('contractCallReceive') // use unique scenario name among all tests
  .from(account)
  .to(contract)
  .blocks(getMixedBlocks())
  .value(10)
  .build();

export {options, run};
