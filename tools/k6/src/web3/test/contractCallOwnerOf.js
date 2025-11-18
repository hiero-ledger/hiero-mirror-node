// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder, getMixedBlocks} from './common.js';

const contract = __ENV.ERC_CONTRACT_ADDRESS;
const selector = '0xd5d03e21';
const token = __ENV.NON_FUNGIBLE_TOKEN_ADDRESS;
const serialNumber = __ENV.SERIAL_NUMBER;

const {options, run} = new ContractCallTestScenarioBuilder()
  .name('contractCallOwnerOf') // use unique scenario name among all tests
  .selector(selector)
  .args([token, serialNumber])
  .to(contract)
  .blocks(getMixedBlocks())
  .build();

export {options, run};
