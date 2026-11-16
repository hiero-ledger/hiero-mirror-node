// SPDX-License-Identifier: Apache-2.0

import {PrecompileModificationTestTemplate} from '../commonPrecompileModificationFunctionsTemplate.js';
import {ContractCallTestScenarioBuilder} from '../common.js';

const contract = __ENV.COMPLEX_FUNCTIONS_CONTRACT_ADDRESS;
const firstReceiver = __ENV.RECEIVER_ADDRESS;
const secondReceiver = __ENV.SPENDER_ADDRESS;
const payer = __ENV.PAYER_ACCOUNT;
const treasury = '0'.repeat(24) + payer; // Pad with 24 zeros to convert payer account from 20 to 32 bytes treasury account
const runMode = __ENV.RUN_WITH_VARIABLES;
const metadata =
  '0000000000000000000000000000000000000000000000000000000000000080' +
  '0000000000000000000000000000000000000000000000000000000000000001' +
  '0000000000000000000000000000000000000000000000000000000000000020' +
  '0000000000000000000000000000000000000000000000000000000000000001' +
  '0200000000000000000000000000000000000000000000000000000000000000';

/*
This test covers the full lifecycle of a Non-Fungible Token
1. Token Creation
2. Associate token
3. GrantTokenKyc
4. Mint token to treasury
5. Transfer token from treasury to account1
6. Freeze / Unfreeze token
7. Transfer token from account1 to account2
8. Wipe amount 10 token from account2
9. Pause / Unpause token
*/
const selector = '0xb046226e'; //nftLifecycle(address firstReceiver,address secondReceiver ,address treasury, bytes[] memory metadata)
const testName = 'contractCallComplexFunctionsNFTLifecycle';

// Balances in tinybars
const BALANCE_100_HBAR = 10_000_000_000;
const BALANCE_1M_HBAR = 100_000_000_000_000;
const BALANCE_10B_HBAR = 1_000_000_000_000_000_000;
const HTS_SYSTEM_CONTRACT = '0000000000000000000000000000000000000167';

// keyTypes mapping slots (KeyHelper.sol, slot 1) with the values set by its constructor
const KEY_TYPE_SLOTS = [
  ['a6eef7e35abe7026729641147f7915573c7e97b47efa546f5f6e3230263bcb49', 1], // ADMIN
  ['cc69885fda6bcc1a4ace058b4a62bf5e179ea78fd58a1ccd71c22cc9b688792f', 2], // KYC
  ['d9d16d34ffb15ba3a3d852f0d403e2ce1d691fb54de27ac87cd2f993f3ec330f', 4], // FREEZE
  ['7dfe757ecd65cbd7922a9c0161e935dd7fdbcc0e999689c7d31633896b1fc60b', 8], // WIPE
  ['edc95719e9a3b28dd8e80877cb5880a9be7de1a13fc8b05e7999683b6b567643', 16], // SUPPLY
  ['e2689cd4a84e23ad2f564004f1c9013e9589d260bde6380aba3ca7e09e4df40c', 32], // FEE
  ['8f331abe73332f95a25873e8b430885974c0409691f89d643119a11623a7924a', 64], // PAUSE
];

const toAddress = (value) => value.slice(-40);
const toSlotValue = (value) => '0x' + value.toString(16).padStart(64, '0');
// System account in the payer's shard and realm
const toSystemAddress = (num) => toAddress(payer).slice(0, 24) + num.toString(16).padStart(16, '0');

const stateOverrides = () => {
  if (!contract || !payer || !firstReceiver || !secondReceiver) {
    return null;
  }

  return [
    {
      address: contract,
      balance: BALANCE_100_HBAR,
      nonce: 1,
      state_diff: KEY_TYPE_SLOTS.map(([key, value]) => ({key: `0x${key}`, value: toSlotValue(value)})),
    },
    {address: toAddress(payer), balance: BALANCE_1M_HBAR},
    {address: toAddress(firstReceiver), balance: BALANCE_100_HBAR},
    {address: toAddress(secondReceiver), balance: BALANCE_100_HBAR},
    {address: toSystemAddress(2), balance: BALANCE_10B_HBAR},
    {address: toSystemAddress(98), balance: BALANCE_1M_HBAR},
    {address: toSystemAddress(800), balance: BALANCE_1M_HBAR},
    {address: toSystemAddress(801), balance: BALANCE_1M_HBAR},
    {address: toSystemAddress(802), balance: BALANCE_1M_HBAR},
    {address: HTS_SYSTEM_CONTRACT, balance: 0},
  ];
};

//If RUN_WITH_VARIABLES=true will run tests with __ENV variables
const {options, run} =
  runMode === 'false'
    ? new PrecompileModificationTestTemplate(testName, false)
    : new ContractCallTestScenarioBuilder()
        .name(testName)
        .selector(selector)
        .args([firstReceiver, secondReceiver, treasury, metadata])
        .from(payer)
        .value(933333333) // Value is needed because the first operation in the contract call is token create
        .to(contract)
        .stateOverrides(stateOverrides)
        .build();

export {options, run};
