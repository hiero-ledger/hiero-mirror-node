/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
