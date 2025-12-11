// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder, getMixedBlocks} from './common.js';
import {ContractCallEstimateTestTemplate} from './commonContractCallEstimateTemplate.js';

const contract = __ENV.ERC_CONTRACT_ADDRESS;
const selector = '0xe1f21c67'; //approve
const token = __ENV.TOKEN_ADDRESS;
const spender = __ENV.SPENDER_ADDRESS;
const amount = __ENV.AMOUNT;
const runMode = __ENV.RUN_WITH_VARIABLES;
const testName = 'estimateERCApprove';

//If RUN_WITH_VARIABLES=true will run tests with __ENV variables
const {options, run} =
  runMode === 'false'
    ? new ContractCallEstimateTestTemplate(testName, false)
    : new ContractCallTestScenarioBuilder()
        .name(testName) // use unique scenario name among all tests
        .selector(selector)
        .args([token, spender, amount])
        .to(contract)
        .blocks(getMixedBlocks())
        .estimate(true)
        .build();

export {options, run};
