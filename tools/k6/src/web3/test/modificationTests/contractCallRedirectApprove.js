// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder} from '../common.js';
import {PrecompileModificationTestTemplate} from '../commonPrecompileModificationFunctionsTemplate.js';
import {PRECOMPILE_TEST_CONTRACT_BYTECODE} from '../resources/precompileTestContractBytecode.js';

const contract = __ENV.PRECOMPILE_CONTRACT;
const selector = '0x911ce425'; //approveRedirect
const token = __ENV.FUNGIBLE_TOKEN_WITH_FREEZE_KEY_ADDRESS;
const spender = __ENV.FUNGIBLE_TOKEN_WITH_FREEZE_KEY_ASSOCIATED_ACCOUNT_ADDRESS;
const amount = __ENV.AMOUNT;
const runMode = __ENV.RUN_WITH_VARIABLES;
const testName = 'contractCallRedirectApprove';

const stateOverrides = () => [{address: contract, code: PRECOMPILE_TEST_CONTRACT_BYTECODE}];

//If RUN_WITH_VARIABLES=true will run tests with __ENV variables
const {options, run} =
  runMode === 'false'
    ? new PrecompileModificationTestTemplate(testName, false)
    : new ContractCallTestScenarioBuilder()
        .name(testName) // use unique scenario name among all tests
        .selector(selector)
        .args([token, spender, amount])
        .to(contract)
        .stateOverrides(stateOverrides)
        .build();

export {options, run};
