// SPDX-License-Identifier: Apache-2.0

import RecordFileV5 from '../../stream/recordFileV5';
import testUtils from './testUtils';

describe('unsupported record file version', () => {
  testUtils.testRecordFileUnsupportedVersion([1, 2, 3, 4, 6], RecordFileV5);
});

describe('canCompact', () => {
  const testSpecs = [
    [1, false],
    [2, false],
    [3, false],
    [4, false],
    [5, true],
    [6, false],
  ];

  testUtils.testRecordFileCanCompact(testSpecs, RecordFileV5);
});

describe('from v5 buffer or compact object', () => {
  testUtils.testRecordFileFromBufferOrObj(5, RecordFileV5, true, true);
});
