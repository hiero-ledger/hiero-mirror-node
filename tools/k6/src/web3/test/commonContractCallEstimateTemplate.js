// SPDX-License-Identifier: Apache-2.0

import {SharedArray} from 'k6/data';
import {ContractCallTestScenarioBuilder} from './common.js';

function ContractCallEstimateTestTemplate(key) {
  const data = new SharedArray(key, () => {
    return JSON.parse(open('./resources/estimate.json'))[key];
  });

  const {options, run} = new ContractCallTestScenarioBuilder().name(key).estimate(true).vuData(data).build();

  return {options, run};
}

export {ContractCallEstimateTestTemplate};
