// SPDX-License-Identifier: Apache-2.0

import {loadVuDataOrDefault, ContractCallTestScenarioBuilder} from './common.js';

function PrecompileModificationTestTemplate(key, shouldRevert) {
  return new ContractCallTestScenarioBuilder()
    .name(key)
    .vuData(loadVuDataOrDefault('../resources/modificationFunctions.json', key))
    .shouldRevert(shouldRevert)
    .build();
}

export {PrecompileModificationTestTemplate};
