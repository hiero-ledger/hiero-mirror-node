// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder, getMixedBlocks} from './common.js';
import {ContractCallEstimateTestTemplate} from './commonContractCallEstimateTemplate.js';

const contract = __ENV.STORAGE_SLOTS_CONTRACT;
const runMode = __ENV.RUN_WITH_VARIABLES;
const data = __ENV.STORAGE_SLOTS_CALLDATA;
const testName = 'estimateReadStorageSlots';

// If RUN_WITH_VARIABLES=false, use the generic estimate template; otherwise build from provided ENV variables
const {options} =
  runMode === 'false'
    ? new ContractCallEstimateTestTemplate(testName, false)
    : new ContractCallTestScenarioBuilder()
        .name(testName) // use unique scenario name among all tests
        .data(data)
        .to(contract)
        // .blocks(getMixedBlocks())
        .estimate(true)
        .build();

export {options};
