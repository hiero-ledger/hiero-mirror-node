// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder} from './common.js';
import {ContractCallEstimateTestTemplate} from './commonContractCallEstimateTemplate.js';

const contract = __ENV.ESTIMATE_PRECOMPILE_CONTRACT;
const selector = '0x84d232f5'; //approveNFTExternal
const token = __ENV.NON_FUNGIBLE_TOKEN_ADDRESS;
const spender = __ENV.SPENDER_ADDRESS;
const serialNumber = __ENV.SERIAL_NUMBER;
const runMode = __ENV.RUN_WITH_VARIABLES;
const testName = 'estimateApproveNft';

//If RUN_WITH_VARIABLES=true will run tests with __ENV variables
const {options, run} =
  runMode === 'false'
    ? new ContractCallEstimateTestTemplate(testName, false)
    : new ContractCallTestScenarioBuilder()
        .name(testName) // use unique scenario name among all tests
        .selector(selector)
        .args([token, spender, serialNumber])
        .to(contract)
        .estimate(true)
        .build();

export {options, run};
