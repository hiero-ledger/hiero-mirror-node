// SPDX-License-Identifier: Apache-2.0

import {requestQueryParser} from '../middleware/requestHandler';

describe('qs tests', () => {
  test('requestQueryParser for empty', () => {
    const val = requestQueryParser('');
    expect(val).toStrictEqual({});
  });

  test('requestQueryParser for null', () => {
    const val = requestQueryParser(null);
    expect(val).toStrictEqual({});
  });

  test('requestQueryParser for single query param', () => {
    const val = requestQueryParser('transactiontype=bar');
    expect(val.transactiontype).toStrictEqual('bar');
  });

  test('requestQueryParser for repeated query params of different cases', () => {
    const val = requestQueryParser('transactiontype=bar&transactionType=xyz');
    expect(val).toStrictEqual({transactiontype: ['bar', 'xyz']});
  });

  test('requestQueryParser for repeated query params of different cases with matching repetitions', () => {
    const val = requestQueryParser('transactiontype=bar&transactionType=xyz&transactionType=ppp');
    expect(val).toStrictEqual({transactiontype: ['bar', 'xyz', 'ppp']});
  });

  test('requestQueryParser for repeated query params of different cases with matching repetitions of account and token ids', () => {
    const val = requestQueryParser(
      'account.id=1&token.id=2&account.Id=lt:3&token.Id=gt:4&account.Id=lte:5&token.Id=gte:6&account.id=7&token.id=8&account.ID=9&token.ID=10'
    );
    expect(val).toStrictEqual({
      'account.id': ['1', '7', 'lt:3', 'lte:5', '9'],
      'token.id': ['2', '8', 'gt:4', 'gte:6', '10'],
    });
  });

  test('requestQueryParser for single lowercased query param values', () => {
    const val = requestQueryParser('order=ASC&result=SUCCESS');
    expect(val.order).toStrictEqual('asc');
    expect(val.result).toStrictEqual('success');
  });

  test('requestQueryParser for multiple lowercased query param keys and values', () => {
    const val = requestQueryParser('order=ASC&ORder=ASC');
    expect(val.order).toStrictEqual(['asc', 'asc']);
  });
});
