// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder, getMixedBlocks} from '../common.js';
import {PrecompileModificationTestTemplate} from '../commonPrecompileModificationFunctionsTemplate.js';

const contract = __ENV.ESTIMATE_PRECOMPILE_CONTRACT;
const selector = '0xceda64c4'; //approveExternal
const token = __ENV.TOKEN_ADDRESS;
const spender = __ENV.ACCOUNT_ADDRESS;
const amount = __ENV.AMOUNT;
const runMode = __ENV.RUN_WITH_VARIABLES;
const testName = 'contractCallPrecompileApprove';

//If RUN_WITH_VARIABLES=true will run tests with __ENV variables
const {options, run} =
  runMode === 'false'
    ? new PrecompileModificationTestTemplate(testName, false)
    : new ContractCallTestScenarioBuilder()
        .name(testName) // use unique scenario name among all tests
        .selector(selector)
        .args([token, spender, amount])
        .to(contract)
        .blocks(getMixedBlocks())
        .build();

export {options, run};
