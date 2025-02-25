// SPDX-License-Identifier: Apache-2.0

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
