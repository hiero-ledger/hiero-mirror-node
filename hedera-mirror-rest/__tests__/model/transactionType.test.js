/*
 * Copyright (C) 2021-2025 Hedera Hashgraph, LLC
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

import {proto} from '@hashgraph/proto';
import {TransactionType} from '../../model';
import {InvalidArgumentError} from '../../errors';

const hederaFunctionalityLength = 86;
const cryptoCreateAccountProtoId = 11;
const unknownProtoId = 9999999;

describe('getName', () => {
  test('Return valid name', () => {
    expect(TransactionType.getName(cryptoCreateAccountProtoId)).toEqual('CRYPTOCREATEACCOUNT');
  });
  test('Return valid custom name', () => {
    expect(TransactionType.getName(53)).toEqual('TOKENUPDATENFTS');
  });
  test('Return UNKNOWN for future proto id', () => {
    expect(TransactionType.getName(unknownProtoId)).toEqual('UNKNOWN');
  });
  test('Return UNKNOWN for invalid proto id', () => {
    expect(TransactionType.getName('')).toEqual('UNKNOWN');
  });
  test('Return UNKNOWN for null proto id', () => {
    expect(TransactionType.getName(null)).toEqual('UNKNOWN');
  });
  test('Return UNKNOWN for undefined proto id', () => {
    expect(TransactionType.getName(undefined)).toEqual('UNKNOWN');
  });
});

describe('getProtoId', () => {
  test('Return valid proto id', () => {
    expect(TransactionType.getProtoId('CRYPTOCREATEACCOUNT')).toEqual(`${cryptoCreateAccountProtoId}`);
  });
  test('Return valid custom proto id', () => {
    expect(TransactionType.getProtoId('TOKENUPDATENFTS')).toEqual('53');
  });
  test('Return valid proto id for camel case', () => {
    expect(TransactionType.getProtoId('cryptoCreateAccount')).toEqual(`${cryptoCreateAccountProtoId}`);
  });
  test('Throw error for invalid name', () => {
    expect(() => {
      TransactionType.getProtoId(unknownProtoId);
    }).toThrow(InvalidArgumentError);
  });
  test('Throw error for unknown name', () => {
    expect(() => {
      TransactionType.getProtoId('UNKNOWN');
    }).toThrow(InvalidArgumentError);
  });
  test('Throw error for null name', () => {
    expect(() => {
      TransactionType.getProtoId(null);
    }).toThrow(InvalidArgumentError);
  });
  test('Throw error for undefined name', () => {
    expect(() => {
      TransactionType.getProtoId(undefined);
    }).toThrow(InvalidArgumentError);
  });
});

describe('isValid', () => {
  test('Return valid proto id', () => {
    expect(TransactionType.isValid('CRYPTOCREATEACCOUNT')).toBeTruthy();
  });
  test('Return valid custom proto id', () => {
    expect(TransactionType.isValid('TOKENUPDATENFTS')).toBeTruthy();
  });
  test('Return valid proto id for camel case', () => {
    expect(TransactionType.isValid('cryptoCreateAccount')).toBeTruthy();
  });
  test('Throw error for invalid name', () => {
    expect(TransactionType.isValid(unknownProtoId)).toBeFalsy();
  });
  test('Throw error for unknown name', () => {
    expect(TransactionType.isValid('')).toBeFalsy();
  });
  test('Throw error for null name', () => {
    expect(TransactionType.isValid(null)).toBeFalsy();
  });
  test('Throw error for undefined name', () => {
    expect(TransactionType.isValid(undefined)).toBeFalsy();
  });
});

describe('transactionType constants are up to date', () => {
  // There isn't a dedicated enum for TransactionBody values, so just check that no new HederaFunctionality exists. If
  // this test fails, ensure that new transaction types are added and update this test with the new HederaFunctionality.
  test('transactionType have new values been added', () => {
    expect(Object.keys(proto.HederaFunctionality).length).toEqual(hederaFunctionalityLength);
  });
});
