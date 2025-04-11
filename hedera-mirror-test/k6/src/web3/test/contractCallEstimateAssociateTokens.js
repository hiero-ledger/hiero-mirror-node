// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder} from './common.js';
import {ContractCallEstimateTestTemplate} from './commonContractCallEstimateTemplate.js';

const contract = __ENV.ESTIMATE_PRECOMPILE_CONTRACT;
const account = __ENV.SPENDER_ADDRESS;
const token = __ENV.TOKEN_ADDRESS;
const runMode = __ENV.RUN_WITH_VARIABLES;
const selector = '0xd91cfc95'; //associateTokenExternal
const testName = 'estimateAssociateTokens';

//If RUN_WITH_VARIABLES=true will run tests with __ENV variables
const {options, run} =
  runMode === 'false'
    ? new ContractCallEstimateTestTemplate(testName, false)
    : new ContractCallTestScenarioBuilder()
        .name(testName) // use unique scenario name among all tests
        .selector(selector)
        .args([account, token])
        .to(contract)
        .estimate(true)
        .build();

export {options, run};
