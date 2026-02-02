// SPDX-License-Identifier: Apache-2.0

import {proto} from '@hiero-ledger/proto';

// models
import {TransactionResult} from '../../model';

describe('transactionResult constants are up to date', () => {
  describe('Name to ID', () => {
    for (const [name, id] of Object.entries(proto.ResponseCodeEnum)) {
      test(name, () => {
        expect(TransactionResult.getProtoId(name)).toEqual(`${id}`);
      });
    }
  });

  describe('ID to Name', () => {
    for (const [name, id] of Object.entries(proto.ResponseCodeEnum)) {
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
