// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder} from './common.js';

const contract = __ENV.DEFAULT_CONTRACT_ADDRESS;
const data = '0x7998a1c4';

const {options, run} = new ContractCallTestScenarioBuilder()
  .name('contractCallIdentifier') // use unique scenario name among all tests
  .data(data)
  .to(contract)
  .build();

export {options, run};
