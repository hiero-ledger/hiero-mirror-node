// SPDX-License-Identifier: Apache-2.0

import {loadVuDataOrDefault, ContractCallTestScenarioBuilder, getMixedBlocks} from './common.js';

function ContractCallEstimateTestTemplate(key) {
  return new ContractCallTestScenarioBuilder()
    .name(key)
    .estimate(true)
    .vuData(loadVuDataOrDefault('./resources/estimate.json', key))
    .blocks(getMixedBlocks())
    .build();
}

export {ContractCallEstimateTestTemplate};
