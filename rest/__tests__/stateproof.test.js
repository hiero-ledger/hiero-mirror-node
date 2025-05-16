// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';
import sinon from 'sinon';

import config from '../config';
import * as constants from '../constants';
import EntityId from '../entityId';
import s3client from '../s3client';
import stateproof from '../stateproof';
import {CompositeRecordFile} from '../stream';
import TransactionId from '../transactionId';
import {opsMap} from '../utils';

global.pool = {};

const clientStub = {};

beforeEach(() => {
  sinon.stub(s3client, 'createS3Client').returns(clientStub);
});

afterEach(() => {
  global.pool = {};
  sinon.restore();
});

const {
  canReachConsensus,
  downloadRecordStreamFilesFromObjectStorage,
  formatCompactableRecordFile,
  getAddressBooksAndNodeAccountIdsByConsensusTimestamp,
  getQueryParamValues,
  getRCDFileInfoByConsensusTimestamp,
  getSuccessfulTransactionConsensusTimestamp,
} = stateproof;

const emptyQueryResult = {
  rows: [],
};

/**
 * @param {sinon.fake} fake
 * @param {Number} expectedCount
 * @param {Array} expectedLastCallParams
 */
const verifyFakeCallCountAndLastCallParamsArg = (fake, expectedCount, expectedLastCallParams) => {
  expect(fake.callCount).toEqual(expectedCount);
  const actualParams = fake.args[fake.args.length - 1][1];
  expect(actualParams).toEqual(expectedLastCallParams);
};

describe('getSuccessfulTransactionConsensusTimestamp', () => {
  const expectedValidConsensusTimestamp = '1234567891000000001';
  const validQueryResult = {
    rows: [{consensus_timestamp: expectedValidConsensusTimestamp}],
  };
  const transactionId = TransactionId.fromString('0.0.1-1234567891-000111222');
  const {maxTransactionConsensusTimestampRangeNs} = config.query;
  const validStart = BigInt(transactionId.getValidStartNs());

  test('with transaction found in db table', async () => {
    const fakeQuery = sinon.fake.resolves(validQueryResult);
    global.pool = {queryQuietly: fakeQuery};

    const consensusTimestamp = await getSuccessfulTransactionConsensusTimestamp(transactionId, 0, false);
    expect(consensusTimestamp).toEqual(expectedValidConsensusTimestamp);
    verifyFakeCallCountAndLastCallParamsArg(fakeQuery, 1, [
      transactionId.getEntityId().getEncodedId(),
      validStart,
      0,
      false,
      validStart + maxTransactionConsensusTimestampRangeNs,
    ]);
  });

  test('with transaction not found', async () => {
    const fakeQuery = sinon.fake.resolves(emptyQueryResult);
    global.pool = {queryQuietly: fakeQuery};

    await expect(getSuccessfulTransactionConsensusTimestamp(transactionId, 0, false)).rejects.toThrow();
    verifyFakeCallCountAndLastCallParamsArg(fakeQuery, 1, [
      transactionId.getEntityId().getEncodedId(),
      validStart,
      0,
      false,
      validStart + maxTransactionConsensusTimestampRangeNs,
    ]);
  });

  test('with db query error', async () => {
    const fakeQuery = sinon.fake.rejects(new Error('db runtime error'));
    global.pool = {queryQuietly: fakeQuery};

    await expect(getSuccessfulTransactionConsensusTimestamp(transactionId, 0, false)).rejects.toThrow();
    verifyFakeCallCountAndLastCallParamsArg(fakeQuery, 1, [
      transactionId.getEntityId().getEncodedId(),
      validStart,
      0,
      false,
      validStart + maxTransactionConsensusTimestampRangeNs,
    ]);
  });
});

describe('getRCDFileInfoByConsensusTimestamp', () => {
  const consensusTimestamp = 1578342501111222333n;
  const upperBound = consensusTimestamp + config.query.maxRecordFileCloseIntervalNs;
  const expectedRCDFileName = '2020-02-09T18_30_25.001721Z.rcd';
  const validQueryResult = {
    rows: [{bytes: null, name: expectedRCDFileName, node_account_id: '3', version: 5}],
  };
  const expectedRCDFileInfo = {
    bytes: null,
    name: expectedRCDFileName,
    nodeAccountId: '0.0.3',
    version: 5,
  };

  test('with record file found', async () => {
    const fakeQuery = sinon.fake.resolves(validQueryResult);
    global.primaryPool = {queryQuietly: fakeQuery};

    const info = await getRCDFileInfoByConsensusTimestamp(consensusTimestamp);
    expect(info).toEqual(expectedRCDFileInfo);
    verifyFakeCallCountAndLastCallParamsArg(fakeQuery, 1, [consensusTimestamp, upperBound]);
  });

  test('with record file not found', async () => {
    const fakeQuery = sinon.fake.resolves(emptyQueryResult);
    global.primaryPool = {queryQuietly: fakeQuery};

    await expect(getRCDFileInfoByConsensusTimestamp(consensusTimestamp)).rejects.toThrow();
    verifyFakeCallCountAndLastCallParamsArg(fakeQuery, 1, [consensusTimestamp, upperBound]);
  });

  test('with db query error', async () => {
    const fakeQuery = sinon.fake.rejects(new Error('db runtime error'));
    global.primaryPool = {queryQuietly: fakeQuery};

    await expect(getRCDFileInfoByConsensusTimestamp(consensusTimestamp)).rejects.toThrow();
    verifyFakeCallCountAndLastCallParamsArg(fakeQuery, 1, [consensusTimestamp, upperBound]);
  });
});

describe('getAddressBooksAndNodeAccountIdsByConsensusTimestamp', () => {
  const nodeAccountId3 = EntityId.parse('0.0.3');
  const nodeAccountId4 = EntityId.parse('0.0.4');
  const nodeAccountId5 = EntityId.parse('0.0.5');
  const nodeAccountIds = [nodeAccountId3, nodeAccountId4, nodeAccountId5];
  const validNodeAccountIds = _.join(
    _.map(nodeAccountIds, (id) => id.getEncodedId()),
    ','
  );
  const validMemos = _.join(
    _.map(nodeAccountIds, (id) => id.toString()),
    ','
  );
  const transactionConsensusTimestamp = '1234567899000000021';

  let queryResultWithNodeAccountIds;
  let queryResultWithMemos;

  beforeEach(() => {
    queryResultWithNodeAccountIds = {
      rows: [
        {
          file_data: 'address book 1 data',
          node_count: 3,
          node_account_ids: validNodeAccountIds,
        },
        {
          file_data: 'address book 2 data',
          node_count: 3,
          node_account_ids: validNodeAccountIds,
        },
      ],
    };

    queryResultWithMemos = {
      rows: [
        {
          file_data: 'address book 1 data',
          node_count: 3,
          memos: validMemos,
        },
        {
          file_data: 'address book 2 data',
          node_count: 3,
          memos: validMemos,
        },
      ],
    };
  });

  const expectPassOrToThrow = async (queryResultOrFakeQueryFunc, expectPass) => {
    let queryResult = {rows: []};
    const queryStub = sinon.stub();
    if (typeof queryResultOrFakeQueryFunc === 'function') {
      queryStub.callsFake(queryResultOrFakeQueryFunc);
    } else {
      queryResult = queryResultOrFakeQueryFunc;
      queryStub.returns(queryResultOrFakeQueryFunc);
    }
    global.primaryPool = {queryQuietly: queryStub};

    if (expectPass) {
      const result = await getAddressBooksAndNodeAccountIdsByConsensusTimestamp(transactionConsensusTimestamp);
      expect(result.addressBooks).toEqual(
        _.map(queryResult.rows, (row) => Buffer.from(row.file_data).toString('base64'))
      );

      let expectedNodeAccountIds;
      const lastRow = _.last(queryResult.rows);
      if (lastRow.node_account_ids) {
        expectedNodeAccountIds = _.map(lastRow.node_account_ids.split(','), (id) => EntityId.parse(id).toString());
      } else {
        expectedNodeAccountIds = lastRow.memos.split(',');
      }
      expect(result.nodeAccountIds.sort()).toEqual(expectedNodeAccountIds.sort());

      expect(queryStub.callCount).toEqual(1);
    } else {
      await expect(
        getAddressBooksAndNodeAccountIdsByConsensusTimestamp(transactionConsensusTimestamp)
      ).rejects.toThrow();
      expect(queryStub.callCount).toEqual(1);
    }
  };

  const selectQueryResult = (hasNodeAccountIds) =>
    hasNodeAccountIds ? queryResultWithNodeAccountIds : queryResultWithMemos;

  const testSpecs = [
    {
      name: 'query result has node_address_ids',
      hasNodeAddressIds: true,
      queryResultName: 'queryResultWithNodeAccountIds',
    },
    {
      name: 'query result has memos',
      hasNodeAddressIds: false,
      queryResultName: 'queryResultWithMemos',
    },
  ];

  testSpecs.forEach((spec) => {
    test(`with matching address books and node account IDs found - ${spec.name}`, async () => {
      await expectPassOrToThrow(selectQueryResult(spec.hasNodeAddressIds), true);
    });

    test(`with node address not found - ${spec.name}`, async () => {
      const queryResult = selectQueryResult(spec.hasNodeAddressIds);
      for (const row of queryResult.rows) {
        if (spec.hasNodeAddressIds) {
          delete row.node_account_ids;
        } else {
          delete row.memos;
        }
      }
      await expectPassOrToThrow(queryResult, false);
    });

    test(`with node address count mismatch count in last address book - ${spec.name}`, async () => {
      const queryResult = selectQueryResult(spec.hasNodeAddressIds);
      const row = _.last(queryResult.rows);
      row.node_count += 1;
      await expectPassOrToThrow(queryResult, false);
    });
  });

  test('with address book not found', async () => {
    await expectPassOrToThrow(emptyQueryResult, false);
  });

  test('with db runtime error', async () => {
    await expectPassOrToThrow(async () => {
      throw new Error('db runtime error');
    }, false);
  });
});

describe('getQueryParamValues', () => {
  const eq = opsMap.eq;
  const expected = {nonce: 0, scheduled: false};
  const testSpecs = [
    {
      name: 'empty',
      filters: [],
      expected: expected,
    },
    {
      name: 'nonce',
      filters: [{key: constants.filterKeys.NONCE, op: eq, value: 1}],
      expected: {nonce: 1, scheduled: false},
    },
    {
      name: 'repeatedNonce',
      filters: [
        {key: constants.filterKeys.NONCE, op: eq, value: 1},
        {key: constants.filterKeys.NONCE, op: eq, value: 2},
        {key: constants.filterKeys.NONCE, op: eq, value: 1},
      ],
      expected: {nonce: 1, scheduled: false},
    },
    {
      name: 'scheduled',
      filters: [{key: constants.filterKeys.SCHEDULED, op: eq, value: true}],
      expected: {nonce: 0, scheduled: true},
    },
    {
      name: 'repeatedScheduled',
      filters: [
        {key: constants.filterKeys.SCHEDULED, op: eq, value: true},
        {key: constants.filterKeys.SCHEDULED, op: eq, value: false},
        {key: constants.filterKeys.SCHEDULED, op: eq, value: false},
      ],
      expected: expected,
    },
    {
      name: 'both',
      filters: [
        {key: constants.filterKeys.NONCE, op: eq, value: 1},
        {key: constants.filterKeys.SCHEDULED, op: eq, value: true},
      ],
      expected: {nonce: 1, scheduled: true},
    },
    {
      name: 'repeatedBoth',
      filters: [
        {key: constants.filterKeys.NONCE, op: eq, value: 1},
        {key: constants.filterKeys.SCHEDULED, op: eq, value: true},
        {key: constants.filterKeys.NONCE, op: eq, value: 2},
        {key: constants.filterKeys.SCHEDULED, op: eq, value: false},
      ],
      expected: {nonce: 2, scheduled: false},
    },
  ];

  for (const testSpec of testSpecs) {
    test(testSpec.name, () => {
      expect(getQueryParamValues(testSpec.filters)).toEqual(testSpec.expected);
    });
  }
});

describe('downloadRecordStreamFilesFromObjectStorage', () => {
  const partialFilePaths = _.map([3, 4, 5, 6], (num) => `0.0.${num}/2020-02-09T18_30_25.001721Z.rcd_sig`);
  const extraFileContent = '123456790123456789012345678901234';

  const errorResponse = {
    Body: {
      transformToByteArray: async () => {
        await new Promise((resolve) => setTimeout(resolve, 1000));
        return Promise.reject(new Error('oops'));
      },
    },
  };

  const getDataResponse = (key) => ({
    Body: {
      transformToByteArray: async () => Uint8Array.from(Buffer.from(key + extraFileContent)),
    },
  });

  beforeEach(() => {
    config.stateproof = {
      streams: {
        bucketName: 'test-bucket-name',
      },
    };
  });

  const stubS3ClientGetObject = (stub) => {
    clientStub.getObject = stub;
  };

  const verifyGetObjectStubAndReturnedFileObjects = (getObjectStub, fileObjects, failedNodes) => {
    let succeededPartialFilePaths = partialFilePaths;
    if (failedNodes) {
      succeededPartialFilePaths = _.filter(partialFilePaths, (partialFilePath) =>
        _.every(failedNodes, (failedNode) => !partialFilePath.startsWith(failedNode))
      );
    }

    expect(_.map(fileObjects, (file) => file.partialFilePath).sort()).toEqual(succeededPartialFilePaths.sort());
    for (const fileObject of fileObjects) {
      const data = constants.recordStreamPrefix + fileObject.partialFilePath + extraFileContent;
      expect(fileObject.data).toEqual(Buffer.from(data));
    }
    expect(getObjectStub.callCount).toEqual(partialFilePaths.length);
    for (const args of getObjectStub.args) {
      const params = args[0];
      expect(params.Key.startsWith(constants.recordStreamPrefix)).toBeTruthy();
      expect(params.RequestPayer).toEqual('requester');
    }
  };

  test('with all files downloaded successfully', async () => {
    const getObjectStub = sinon.stub().callsFake(async (params, callback) => getDataResponse(params.Key));
    stubS3ClientGetObject(getObjectStub);

    const fileObjects = await downloadRecordStreamFilesFromObjectStorage(...partialFilePaths);
    verifyGetObjectStubAndReturnedFileObjects(getObjectStub, fileObjects);
  });

  test('with all files failed to download', async () => {
    const getObjectStub = sinon.stub().callsFake(async (params, callback) => errorResponse);
    stubS3ClientGetObject(getObjectStub);

    const fileObjects = await downloadRecordStreamFilesFromObjectStorage(...partialFilePaths);
    const failedNodes = _.map([3, 4, 5, 6], (num) => `0.0.${num}`);
    verifyGetObjectStubAndReturnedFileObjects(getObjectStub, fileObjects, failedNodes);
  });

  test('with download failed for 0.0.3', async () => {
    const getObjectStub = sinon.stub().callsFake(async (params, callback) => {
      const {Key: key} = params;
      return key.includes('0.0.3') ? errorResponse : getDataResponse(key);
    });
    stubS3ClientGetObject(getObjectStub);

    const fileObjects = await downloadRecordStreamFilesFromObjectStorage(...partialFilePaths);
    verifyGetObjectStubAndReturnedFileObjects(getObjectStub, fileObjects, ['0.0.3']);
  });
});

describe('canReachConsensus', () => {
  test('with exactly 1/3 count', () => {
    expect(canReachConsensus(1, 3)).toBeTruthy();
  });

  test('with more than 1/3 when rounded up', () => {
    expect(canReachConsensus(2, 4)).toBeTruthy();
  });

  test('with less than 1/3', () => {
    expect(canReachConsensus(1, 4)).toBeFalsy();
  });
});

describe('formatCompactableRecordFile', () => {
  test('format', () => {
    const stub = sinon.createStubInstance(CompositeRecordFile, {
      toCompactObject: {
        head: Buffer.from([1]),
        startRunningHashObject: Buffer.from([2]),
        hashesBefore: [Buffer.from([3])],
        recordStreamObject: Buffer.from([4]),
        hashesAfter: [Buffer.from([5])],
        endRunningHashObject: Buffer.from([6]),
      },
    });
    const expected = {
      head: Buffer.from([1]).toString('base64'),
      start_running_hash_object: Buffer.from([2]).toString('base64'),
      hashes_before: [Buffer.from([3]).toString('base64')],
      record_stream_object: Buffer.from([4]).toString('base64'),
      hashes_after: [Buffer.from([5]).toString('base64')],
      end_running_hash_object: Buffer.from([6]).toString('base64'),
    };

    const actual = formatCompactableRecordFile(stub, '0.0.1-123-12345678', false);
    expect(actual).toEqual(expected);
  });

  test('error', () => {
    const stub = sinon.createStubInstance(CompositeRecordFile, {
      toCompactObject: sinon.stub().throws(new Error('oops')),
    });

    expect(() => formatCompactableRecordFile(stub, '0.0.1-123-12345678', false)).toThrowErrorMatchingSnapshot();
  });
});
