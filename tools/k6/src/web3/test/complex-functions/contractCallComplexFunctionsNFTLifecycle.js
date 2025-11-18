// SPDX-License-Identifier: Apache-2.0

import {PrecompileModificationTestTemplate} from '../commonPrecompileModificationFunctionsTemplate.js';
import {ContractCallTestScenarioBuilder, getMixedBlocks} from '../common.js';

const contract = __ENV.COMPLEX_FUNCTIONS_CONTRACT_ADDRESS;
const firstReceiver = __ENV.RECEIVER_ADDRESS;
const secondReceiver = __ENV.SPENDER_ADDRESS;
const payer = __ENV.PAYER_ACCOUNT;
const treasury = '0'.repeat(24) + payer; // Pad with 24 zeros to convert payer account from 20 to 32 bytes treasury account
const runMode = __ENV.RUN_WITH_VARIABLES;
const metadata =
  '0000000000000000000000000000000000000000000000000000000000000080' +
  '0000000000000000000000000000000000000000000000000000000000000001' +
  '0000000000000000000000000000000000000000000000000000000000000020' +
  '0000000000000000000000000000000000000000000000000000000000000001' +
  '0200000000000000000000000000000000000000000000000000000000000000';

/*
This test covers the full lifecycle of a Non-Fungible Token
1. Token Creation
2. Associate token
3. GrantTokenKyc
4. Mint token to treasury
5. Transfer token from treasury to account1
6. Freeze / Unfreeze token
7. Transfer token from account1 to account2
8. Wipe amount 10 token from account2
9. Pause / Unpause token
*/
const selector = '0xb046226e'; //nftLifecycle(address firstReceiver,address secondReceiver ,address treasury, bytes[] memory metadata)
const testName = 'contractCallComplexFunctionsNFTLifecycle';

//If RUN_WITH_VARIABLES=true will run tests with __ENV variables
const {options, run} =
  runMode === 'false'
    ? new PrecompileModificationTestTemplate(testName, false)
    : new ContractCallTestScenarioBuilder()
        .name(testName)
        .selector(selector)
        .args([firstReceiver, secondReceiver, treasury, metadata])
        .from(payer)
        .value(933333333) // Value is needed because the first operation in the contract call is token create
        .to(contract)
        .blocks(getMixedBlocks())
        .build();

export {options, run};
