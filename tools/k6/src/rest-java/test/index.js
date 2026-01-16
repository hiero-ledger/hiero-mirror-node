// SPDX-License-Identifier: Apache-2.0

import {getSequentialTestScenarios} from '../../lib/common.js';

// import test modules
import * as accountsNftAllowanceOwner from './accountsNftAllowanceOwner.js';
import * as accountsNftAllowanceSpender from './accountsNftAllowanceSpender.js';
import * as accountsPendingAirdrop from './accountsPendingAirdrop.js';
import * as accountsOutstandingAirdrop from './accountsOutstandingAirdrop.js';
import * as hooks from './hooks.js';
import * as hookStorage from './hookStorage.js';
import * as networkExchangeRate from './networkExchangeRate.js';
import * as networkFees from './networkFees.js';
import * as networkStake from './networkStake.js';
import * as networkSupply from './networkSupply.js';
import * as topicsId from './topicsId.js';
import * as rampUp from './rampUp.js';

// add test modules here
const tests = {
  accountsNftAllowanceOwner,
  accountsNftAllowanceSpender,
  accountsPendingAirdrop,
  accountsOutstandingAirdrop,
  hooks,
  hookStorage,
  networkExchangeRate,
  networkFees,
  networkStake,
  networkSupply,
  topicsId,
  rampUp,
};

const {funcs, getUrlFuncs, options, requiredParameters, scenarioDurationGauge, scenarios} = getSequentialTestScenarios(
  tests,
  'RESTJAVA'
);

export {funcs, getUrlFuncs, options, requiredParameters, scenarioDurationGauge, scenarios};
