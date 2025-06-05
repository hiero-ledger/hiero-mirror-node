// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder} from './common.js';
import {ContractCallEstimateTestTemplate} from './commonContractCallEstimateTemplate.js';

const contract = __ENV.STORAGE_SLOTS_CONTRACT;
const runMode = __ENV.RUN_WITH_VARIABLES;
const data = __ENV.STORAGE_SLOTS_CALLDATA;
const testName = 'estimateReadStorageSlots';

//If RUN_WITH_VARIABLES=true will run tests with __ENV variables
const {options, run} =
  runMode === 'false'
    ? new ContractCallEstimateTestTemplate(testName, false)
    : new ContractCallTestScenarioBuilder()
        .name(testName) // use unique scenario name among all tests
        .data(data)
        .to(contract)
        .estimate(true)
        .build();

export {options, run};
