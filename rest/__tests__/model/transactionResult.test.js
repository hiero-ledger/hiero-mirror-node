// SPDX-License-Identifier: Apache-2.0

import {ResponseCodeEnum} from '../../gen/services/response_code_pb.js';

// models
import {TransactionResult} from '../../model';

const responseCodeEntries = Object.entries(ResponseCodeEnum).filter(([, id]) => typeof id === 'number');

describe('transactionResult constants are up to date', () => {
  describe('Name to ID', () => {
    for (const [name, id] of responseCodeEntries) {
      test(name, () => {
        expect(TransactionResult.getProtoId(name)).toEqual(`${id}`);
      });
    }
  });

  describe('ID to Name', () => {
    for (const [name, id] of responseCodeEntries) {
      test(`${id}`, () => {
        expect(TransactionResult.getName(id)).toEqual(name);
      });
    }
  });
});

describe('transactionResults getters work as expected', () => {
  test('getName handles unknown', () => {
    expect(TransactionResult.getName(9999999)).toEqual('UNKNOWN');
  });

  test('getProtoId handles unknown', () => {
    expect(TransactionResult.getProtoId('XYZ')).toBeFalsy();
  });

  test('getSuccessProtoIds', () => {
    expect(TransactionResult.getSuccessProtoIds()).toEqual([22, 104, 220]);
  });
});
