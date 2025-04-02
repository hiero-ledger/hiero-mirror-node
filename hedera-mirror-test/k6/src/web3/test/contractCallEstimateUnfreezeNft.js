// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder} from './common.js';
import {ContractCallEstimateTestTemplate} from './commonContractCallEstimateTemplate.js';

const contract = __ENV.ESTIMATE_PRECOMPILE_CONTRACT;
const account = __ENV.NON_FUNGIBLE_TOKEN_WITH_FREEZE_KEY_ASSOCIATED_ACCOUNT_ADDRESS;
const token = __ENV.NON_FUNGIBLE_TOKEN_WITH_FREEZE_KEY_ADDRESS;
const runMode = __ENV.RUN_WITH_VARIABLES;
const selector = '0xe95a71e5'; //unfreezeTokenExternal
const testName = 'estimateUnfreezeNft';

//If RUN_WITH_VARIABLES=true will run tests with __ENV variables
const {options, run} =
  runMode === 'false'
    ? new ContractCallEstimateTestTemplate(testName, false)
    : new ContractCallTestScenarioBuilder()
        .name(testName) // use unique scenario name among all tests
        .selector(selector)
        .args([token, account])
        .to(contract)
        .estimate(true)
        .build();

export {options, run};
