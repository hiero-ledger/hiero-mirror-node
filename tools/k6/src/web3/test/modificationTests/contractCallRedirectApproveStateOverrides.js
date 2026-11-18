// SPDX-License-Identifier: Apache-2.0

import {check} from 'k6';
import {getOptionsWithScenario, recordRequestDuration} from '../../../lib/common.js';
import {isNonErrorResponse, jsonPost} from '../common.js';
import {PRECOMPILE_TEST_CONTRACT_BYTECODE} from '../resources/precompileTestContractBytecode.js';

const contract = __ENV.PRECOMPILE_CONTRACT;
const payer = __ENV.PAYER_ACCOUNT;
const selector = '0x911ce425'; //approveRedirect
const token = __ENV.FUNGIBLE_TOKEN_WITH_FREEZE_KEY_ADDRESS;
const spender = __ENV.FUNGIBLE_TOKEN_WITH_FREEZE_KEY_ASSOCIATED_ACCOUNT_ADDRESS;
const amount = __ENV.AMOUNT;
const testName = 'contractCallRedirectApproveStateOverrides';
const url = `${__ENV.BASE_URL_PREFIX}/contracts/call`;

// Balances in tinybars
const CONTRACT_BALANCE = 10_000_000_000;
const PAYER_BALANCE = 100_000_000_000_000;

const toSlot = (value) => '0x' + value.toString(16).padStart(64, '0');

// state and state_diff are mutually exclusive per account, so each is set on a different account
const STATE_OVERRIDES = [
  {
    address: contract,
    balance: CONTRACT_BALANCE,
    code: PRECOMPILE_TEST_CONTRACT_BYTECODE,
    nonce: 1,
    state_diff: [100, 101, 102].map((slot) => ({key: toSlot(slot), value: toSlot(slot + 1)})),
  },
  {
    address: payer,
    balance: PAYER_BALANCE,
    nonce: 1,
    state: [{key: toSlot(100), value: toSlot(1)}],
  },
];

const payload = JSON.stringify({
  block: 'latest',
  data: selector + token + spender + amount,
  estimate: false,
  from: payer,
  state_overrides: STATE_OVERRIDES,
  to: contract,
});

const options = getOptionsWithScenario(testName, null, {suite: 'WEB3', url: '/contracts/call'});

const run = function () {
  const response = jsonPost(url, payload);
  const passed = check(response, {[testName]: (r) => isNonErrorResponse(r)});
  recordRequestDuration(response, passed);
};

export {options, run};
