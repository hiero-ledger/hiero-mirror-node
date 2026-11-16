// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder} from './common.js';
import {ContractCallEstimateTestTemplate} from './commonContractCallEstimateTemplate.js';
import {TICK_LENS_POOL_STORAGE} from './resources/tickLensPoolStorage.js';

const contract = __ENV.STORAGE_SLOTS_CONTRACT;
const runMode = __ENV.RUN_WITH_VARIABLES;
const data = __ENV.STORAGE_SLOTS_CALLDATA;
const testName = 'contractCallEstimateReadStorage';

const TICK_LENS_SELECTOR = '0xc1ed38bc';

// Patch only the overridden pool slots, the remaining ticks are still read from the DB
const stateOverrides = () => {
  if (!data) {
    return null;
  }

  return Object.entries(TICK_LENS_POOL_STORAGE).map(([address, storage]) => ({
    address,
    state_diff: Object.entries(storage).map(([key, value]) => ({key, value})),
  }));
};

//If RUN_WITH_VARIABLES=true will run tests with __ENV variables
const {options, run} =
  runMode === 'false'
    ? new ContractCallEstimateTestTemplate(testName, false)
    : new ContractCallTestScenarioBuilder()
        .name(testName) // use unique scenario name among all tests
        .data(data)
        .to(contract)
        .estimate(true)
        .stateOverrides(stateOverrides)
        .build();

export {options, run};
