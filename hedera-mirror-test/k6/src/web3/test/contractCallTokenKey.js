// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder} from './common.js';

const contract = __ENV.HTS_CONTRACT_ADDRESS;
const selector = '0x1955de0b';
const token = __ENV.TOKEN_ADDRESS;
const keyType = __ENV.KEY_TYPE;

const {options, run} = new ContractCallTestScenarioBuilder()
  .name('contractCallTokenKey') // use unique scenario name among all tests
  .selector(selector)
  .args([token, keyType])
  .to(contract)
  .build();

export {options, run};
