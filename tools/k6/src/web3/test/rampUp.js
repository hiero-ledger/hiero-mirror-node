// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder, getMixedBlocks} from './common.js';

const contract = __ENV.HTS_CONTRACT_ADDRESS;
const selector = '0xbff9834f';
const token = __ENV.TOKEN_ADDRESS;

// call isToken to ramp up
const {options, run} = new ContractCallTestScenarioBuilder()
  .name('contractCallRampUp') // use unique scenario name among all tests
  .args([token])
  .selector(selector)
  .scenario({
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      {
        duration: __ENV.DEFAULT_RAMPUP_DURATION || __ENV.DEFAULT_DURATION,
        target: __ENV.DEFAULT_RAMPUP_VUS || __ENV.DEFAULT_VUS,
      },
    ],
    gracefulRampDown: '0s',
  })
  .to(contract)
  .blocks(getMixedBlocks())
  .build();

export {options, run};
