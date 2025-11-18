// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder, getMixedBlocks} from './common.js';
import {ContractCallEstimateTestTemplate} from './commonContractCallEstimateTemplate.js';

const contract = __ENV.ESTIMATE_PRECOMPILE_CONTRACT;
const token = __ENV.NON_FUNGIBLE_TOKEN_ADDRESS;
const amount = '0000000000000000000000000000000000000000000000000000000000000000';
const metadata =
  '0000000000000000000000000000000000000000000000000000000000000060' +
  '0000000000000000000000000000000000000000000000000000000000000001' +
  '0000000000000000000000000000000000000000000000000000000000000020' +
  '000000000000000000000000000000000000000000000000000000000000000c' +
  'eee8fa2d815e9d9c0d2505ab0000000000000000000000000000000000000000';
const runMode = __ENV.RUN_WITH_VARIABLES;
const selector = '0x0c0295d4'; //mintTokenExternal
const testName = 'estimateMintNft';

//If RUN_WITH_VARIABLES=true will run tests with __ENV variables
const {options, run} =
  runMode === 'false'
    ? new ContractCallEstimateTestTemplate(testName, false)
    : new ContractCallTestScenarioBuilder()
        .name(testName) // use unique scenario name among all tests
        .selector(selector)
        .args([token, amount, metadata])
        .to(contract)
        .blocks(getMixedBlocks())
        .estimate(true)
        .build();

export {options, run};
