// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder} from './common.js';
import {ContractCallEstimateTestTemplate} from './commonContractCallEstimateTemplate.js';

const contract = __ENV.ESTIMATE_PRECOMPILE_CONTRACT;
const treasury = __ENV.ACCOUNT_ADDRESS;
const token = __ENV.TOKEN_ADDRESS;
const from = __ENV.PAYER_ACCOUNT;
const runMode = __ENV.RUN_WITH_VARIABLES;
const selector = '0x8ba74da0'; //createFungibleTokenWithCustomFeesPublic
const testName = 'estimateCreateFungibleTokenWithCustomFees';

//If RUN_WITH_VARIABLES=true will run tests with __ENV variables
const {options, run} =
  runMode === 'false'
    ? new ContractCallEstimateTestTemplate(testName, false)
    : new ContractCallTestScenarioBuilder()
        .name(testName) // use unique scenario name among all tests
        .selector(selector)
        .args([treasury, token])
        .to(contract)
        .from(from)
        .estimate(true)
        .value(5000000000)
        .build();

export {options, run};
