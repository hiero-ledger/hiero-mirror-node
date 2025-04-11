// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder} from './common.js';
import {ContractCallEstimateTestTemplate} from './commonContractCallEstimateTemplate.js';

const contract = __ENV.ESTIMATE_PRECOMPILE_CONTRACT;
const account = __ENV.FUNGIBLE_TOKEN_WITH_FREEZE_KEY_ASSOCIATED_ACCOUNT_ADDRESS;
const token = __ENV.FUNGIBLE_TOKEN_WITH_FREEZE_KEY_ADDRESS;
const selector = '0x9c219247'; //dissociateTokenExternal
const runMode = __ENV.RUN_WITH_VARIABLES;
const testName = 'estimateTokenDissociate';

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
