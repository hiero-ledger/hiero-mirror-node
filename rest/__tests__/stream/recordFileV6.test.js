// SPDX-License-Identifier: Apache-2.0

import RecordFileV6 from '../../stream/recordFileV6';
import testUtils from './testUtils';

describe('unsupported record file version', () => {
  testUtils.testRecordFileUnsupportedVersion([1, 2, 3, 4, 5], RecordFileV6);
});

describe('canCompact', () => {
  const testSpecs = [
    [1, false],
    [2, false],
    [3, false],
    [4, false],
    [5, false],
    [6, true],
  ];

  testUtils.testRecordFileCanCompact(testSpecs, RecordFileV6);
});

describe('from v6 buffer or compact object', () => {
  testUtils.testRecordFileFromBufferOrObj(6, RecordFileV6, true, true);
});
