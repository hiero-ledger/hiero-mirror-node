// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder, getMixedBlocks} from './common.js';
import {ContractCallEstimateTestTemplate} from './commonContractCallEstimateTemplate.js';

const contract = __ENV.ESTIMATE_PRECOMPILE_CONTRACT;
const treasury = __ENV.ACCOUNT_ADDRESS;
const token = __ENV.TOKEN_ADDRESS;
const from = __ENV.PAYER_ACCOUNT;
const runMode = __ENV.RUN_WITH_VARIABLES;
const selector = '0x0488c939'; //createNonFungibleTokenWithCustomFeesPublic
const testName = 'estimateCreateNFTWithCustomFees';

//If RUN_WITH_VARIABLES=true will run tests with __ENV variables
const {options, run} =
  runMode === 'false'
    ? new ContractCallEstimateTestTemplate(testName, false)
    : new ContractCallTestScenarioBuilder()
        .name(testName) // use unique scenario name among all tests
        .selector(selector)
        .args([treasury, token])
        .to(contract)
        .blocks(getMixedBlocks())
        .from(from)
        .estimate(true)
        .value(5000000000)
        .build();

export {options, run};
