// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder, getMixedBlocks} from '../common.js';
import {PrecompileModificationTestTemplate} from '../commonPrecompileModificationFunctionsTemplate.js';

const contract = __ENV.ESTIMATE_PRECOMPILE_CONTRACT;
const sender = __ENV.ACCOUNT_ADDRESS;
const receiver = __ENV.ASSOCIATED_ACCOUNT;
const token = __ENV.TOKEN_ADDRESS;
const amount = __ENV.AMOUNT;
const runMode = __ENV.RUN_WITH_VARIABLES;
const selector = '0x4fd6ce0a'; //transferTokenExternal
const testName = 'contractCallPrecompileTransferFungibleToken';

//If RUN_WITH_VARIABLES=true will run tests with __ENV variables
const {options, run} =
  runMode === 'false'
    ? new PrecompileModificationTestTemplate(testName, false)
    : new ContractCallTestScenarioBuilder()
        .name(testName) // use unique scenario name among all tests
        .selector(selector)
        .args([token, sender, receiver, amount])
        .to(contract)
        .blocks(getMixedBlocks())
        .build();

export {options, run};
