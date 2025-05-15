// SPDX-License-Identifier: Apache-2.0

import {getSequentialTestScenarios} from '../../lib/common.js';

// import test modules
import * as accountBalance from './accountBalance.js';
import * as block from './block.js';
import * as blockTransaction from './blockTransaction.js';
import * as constructionCombine from './constructionCombine.js';
import * as constructionHash from './constructionHash.js';
import * as constructionParse from './constructionParse.js';
import * as constructionPayloads from './constructionPayloads.js';
import * as constructionPreprocess from './constructionPreprocess.js';
import * as networkList from './networkList.js';
import * as networkOptions from './networkOptions.js';
import * as networkStatus from './networkStatus.js';

// add test modules here
const tests = {
  accountBalance,
  block,
  blockTransaction,
  constructionCombine,
  constructionHash,
  constructionParse,
  constructionPayloads,
  constructionPreprocess,
  networkList,
  networkOptions,
  networkStatus,
};

const {funcs, options, scenarioDurationGauge, scenarios} = getSequentialTestScenarios(tests, 'ROSETTA');

export {funcs, options, scenarioDurationGauge, scenarios};
