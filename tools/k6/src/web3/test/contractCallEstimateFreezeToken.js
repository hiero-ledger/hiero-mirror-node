// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder, getMixedBlocks} from './common.js';
import {ContractCallEstimateTestTemplate} from './commonContractCallEstimateTemplate.js';

const contract = __ENV.ESTIMATE_PRECOMPILE_CONTRACT;
const account = __ENV.TOKEN_FREEZE_KEY_ACCOUNT_ADDRESS;
const token = __ENV.FUNGIBLE_TOKEN_WITH_FREEZE_KEY_ADDRESS;
const runMode = __ENV.RUN_WITH_VARIABLES;
const selector = '0x9333700b'; //freezeTokenExternal
const testName = 'estimateFreezeToken';

//If RUN_WITH_VARIABLES=true will run tests with __ENV variables
const {options, run} =
  runMode === 'false'
    ? new ContractCallEstimateTestTemplate(testName, false)
    : new ContractCallTestScenarioBuilder()
        .name(testName) // use unique scenario name among all tests
        .selector(selector)
        .args([token, account])
        .to(contract)
        .blocks(getMixedBlocks())
        .estimate(true)
        .build();

export {options, run};
