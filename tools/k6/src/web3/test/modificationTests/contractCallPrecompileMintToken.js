// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder, getMixedBlocks} from '../common.js';
import {PrecompileModificationTestTemplate} from '../commonPrecompileModificationFunctionsTemplate.js';

const contract = __ENV.ESTIMATE_PRECOMPILE_CONTRACT;
const selector = '0x0c0295d4'; //mintTokenExternal
const token = __ENV.TOKEN_ADDRESS;
const amount = __ENV.AMOUNT;
const runMode = __ENV.RUN_WITH_VARIABLES;
const testName = 'contractCallPrecompileMintToken';
const emptyByteArray =
  '0000000000000000000000000000000000000000000000000000000000000060' +
  '0000000000000000000000000000000000000000000000000000000000000000';

//If RUN_WITH_VARIABLES=true will run tests with __ENV variables
const {options, run} =
  runMode === 'false'
    ? new PrecompileModificationTestTemplate(testName, false)
    : new ContractCallTestScenarioBuilder()
        .name(testName) // use unique scenario name among all tests
        .selector(selector)
        .args([token, amount, emptyByteArray])
        .to(contract)
        .blocks(getMixedBlocks())
        .build();

export {options, run};
