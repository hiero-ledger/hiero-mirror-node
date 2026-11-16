// SPDX-License-Identifier: Apache-2.0

import {ContractCallTestScenarioBuilder, getMixedBlocks} from './common.js';

const contract = __ENV.DEFAULT_CONTRACT_ADDRESS;
const account = __ENV.PAYER_ACCOUNT;
const testName = 'contractCallReceive';

// Balances in tinybars. An overridden payer balance must cover the value and gas.
const PAYER_BALANCE = 100_000_000_000_000;
const CONTRACT_BALANCE = 0;

const stateOverrides = () => {
  if (!account || !contract) {
    return null;
  }

  return [
    {address: account.slice(-40), balance: PAYER_BALANCE},
    {address: contract, balance: CONTRACT_BALANCE},
  ];
};

const {options, run} = new ContractCallTestScenarioBuilder()
  .name(testName) // use unique scenario name among all tests
  .from(account)
  .to(contract)
  .blocks(getMixedBlocks())
  .value(10)
  .stateOverrides(stateOverrides)
  .build();

export {options, run};
