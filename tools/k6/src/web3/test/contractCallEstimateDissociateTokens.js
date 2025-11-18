// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder, getMixedBlocks} from './common.js';
import {ContractCallEstimateTestTemplate} from './commonContractCallEstimateTemplate.js';

const contract = __ENV.ESTIMATE_PRECOMPILE_CONTRACT;
const account = __ENV.FUNGIBLE_TOKEN_WITH_FREEZE_KEY_ASSOCIATED_ACCOUNT_ADDRESS;
const token = __ENV.FUNGIBLE_TOKEN_WITH_FREEZE_KEY_ADDRESS;
const selector = '0x2390c1fa'; //dissociateTokensExternal
const runMode = __ENV.RUN_WITH_VARIABLES;
const testName = 'estimateDissociateTokens';
//ABI encoded slot params that describe the token array parameters in the contract call data (Offset 64 bytes and Length 1)
const data =
  '0000000000000000000000000000000000000000000000000000000000000040' +
  '0000000000000000000000000000000000000000000000000000000000000001';

//If RUN_WITH_VARIABLES=true will run tests with __ENV variables
const {options, run} =
  runMode === 'false'
    ? new ContractCallEstimateTestTemplate(testName, false)
    : new ContractCallTestScenarioBuilder()
        .name(testName) // use unique scenario name among all tests
        .selector(selector)
        .args([account, data, token])
        .to(contract)
        .blocks(getMixedBlocks())
        .estimate(true)
        .build();

export {options, run};
