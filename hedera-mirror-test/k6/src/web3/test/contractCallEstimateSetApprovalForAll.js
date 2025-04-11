// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder} from './common.js';
import {ContractCallEstimateTestTemplate} from './commonContractCallEstimateTemplate.js';

const contract = __ENV.ESTIMATE_PRECOMPILE_CONTRACT;
const selector = '0xe31b839c'; //setApprovalForAllExternal
const token = __ENV.NON_FUNGIBLE_TOKEN_ADDRESS;
const account = __ENV.ACCOUNT_ADDRESS;
const trueValue = __ENV.AMOUNT;
const runMode = __ENV.RUN_WITH_VARIABLES;
const testName = 'estimateSetApprovalForAll';

//If RUN_WITH_VARIABLES=true will run tests with __ENV variables
const {options, run} =
  runMode === 'false'
    ? new ContractCallEstimateTestTemplate(testName, false)
    : new ContractCallTestScenarioBuilder()
        .name(testName) // use unique scenario name among all tests
        .selector(selector)
        .args([token, account, trueValue])
        .to(contract)
        .estimate(true)
        .build();

export {options, run};
