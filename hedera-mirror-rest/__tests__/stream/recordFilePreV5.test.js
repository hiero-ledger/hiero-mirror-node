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

import RecordFilePreV5 from '../../stream/recordFilePreV5';
import testUtils from './testUtils';

describe('unsupported record file version', () => {
  testUtils.testRecordFileUnsupportedVersion([3, 4, 5, 6], RecordFilePreV5);
});

describe('canCompact', () => {
  const testSpecs = [
    [1, false],
    [2, false],
    [3, false],
    [4, false],
    [5, false],
    [6, false],
  ];

  testUtils.testRecordFileCanCompact(testSpecs, RecordFilePreV5);
});

describe('from v2 buffer', () => {
  testUtils.testRecordFileFromBufferOrObj(2, RecordFilePreV5);
});
