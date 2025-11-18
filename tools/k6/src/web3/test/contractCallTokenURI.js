// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder, getMixedBlocks} from './common.js';

const contract = __ENV.ERC_CONTRACT_ADDRESS;
const selector = '0xe9dc6375';
const token = __ENV.NON_FUNGIBLE_TOKEN_ADDRESS;
const serialNumber = __ENV.SERIAL_NUMBER;

const {options, run} = new ContractCallTestScenarioBuilder()
  .name('contractCallTokenURI') // use unique scenario name among all tests
  .selector(selector)
  .args([token, serialNumber])
  .to(contract)
  .blocks(getMixedBlocks())
  .build();

export {options, run};
