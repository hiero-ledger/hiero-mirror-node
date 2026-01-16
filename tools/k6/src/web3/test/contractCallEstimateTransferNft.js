// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder} from './common.js';
import {ContractCallEstimateTestTemplate} from './commonContractCallEstimateTemplate.js';

const contract = __ENV.ESTIMATE_PRECOMPILE_CONTRACT;
const sender = __ENV.ACCOUNT_ADDRESS;
const receiver = __ENV.ASSOCIATED_ACCOUNT;
const token = __ENV.NON_FUNGIBLE_TOKEN_ADDRESS;
const amount = __ENV.SERIAL_NUMBER;
const runMode = __ENV.RUN_WITH_VARIABLES;
const selector = '0xbafa6a91'; //transferNFTExternal
const testName = 'estimateTransferNft';

//If RUN_WITH_VARIABLES=true will run tests with __ENV variables
const {options, run} =
  runMode === 'false'
    ? new ContractCallEstimateTestTemplate(testName, false)
    : new ContractCallTestScenarioBuilder()
        .name(testName) // use unique scenario name among all tests
        .selector(selector)
        .args([token, sender, receiver, amount])
        .to(contract)
        .build();

export {options, run};
