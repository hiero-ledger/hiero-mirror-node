// SPDX-License-Identifier: Apache-2.0

import {CompositeRecordFile} from '../../stream';
import testUtils from './testUtils';

describe('unsupported record file version', () => {
  testUtils.testRecordFileUnsupportedVersion([3, 4, 7], CompositeRecordFile);
});

describe('canCompact', () => {
  const testSpecs = [
    [1, false],
    [2, false],
    [3, false],
    [4, false],
    [5, true],
    [6, true],
  ];

  testUtils.testRecordFileCanCompact(testSpecs, CompositeRecordFile);
});

describe('from record file buffer or compact object', () => {
  testUtils.testRecordFileFromBufferOrObj(2, CompositeRecordFile);
  testUtils.testRecordFileFromBufferOrObj(5, CompositeRecordFile, true, true);
  testUtils.testRecordFileFromBufferOrObj(6, CompositeRecordFile, true, true);
});
