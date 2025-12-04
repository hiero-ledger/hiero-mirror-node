// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder, getMixedBlocks} from '../common.js';
import {PrecompileModificationTestTemplate} from '../commonPrecompileModificationFunctionsTemplate.js';

const contract = __ENV.ESTIMATE_PRECOMPILE_CONTRACT;
const account = __ENV.ACCOUNT_ADDRESS;
const token = __ENV.TOKEN_ADDRESS;
const selector = '0x437dffd5'; //nestedAssociateTokenExternal
const runMode = __ENV.RUN_WITH_VARIABLES;
const testName = 'contractCallPrecompileNestedAssociate';

//If RUN_WITH_VARIABLES=true will run tests with __ENV variables
const {options, run} =
  runMode === 'false'
    ? new PrecompileModificationTestTemplate(testName, true)
    : new ContractCallTestScenarioBuilder()
        .name(testName) // use unique scenario name among all tests
        .selector(selector)
        .args([account, token])
        .to(contract)
        .blocks(getMixedBlocks())
        .shouldRevert(true)
        .build();

export {options, run};
