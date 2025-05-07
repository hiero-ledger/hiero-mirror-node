// SPDX-License-Identifier: Apache-2.0

import {SharedArray} from 'k6/data';
import {ContractCallTestScenarioBuilder} from './common.js';

function PrecompileModificationTestTemplate(key, shouldRevert) {
  const data = new SharedArray(key, () => {
    return JSON.parse(open('../resources/modificationFunctions.json'))[key];
  });

  const {options, run} = new ContractCallTestScenarioBuilder()
    .name(key)
    .vuData(data)
    .shouldRevert(shouldRevert)
    .build();

  return {options, run};
}

export {PrecompileModificationTestTemplate};
