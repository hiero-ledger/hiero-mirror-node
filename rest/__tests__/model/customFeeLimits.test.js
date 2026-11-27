// SPDX-License-Identifier: Apache-2.0

import {jest} from '@jest/globals';
import {create, toBinary} from '@bufbuild/protobuf';
import {AccountIDSchema} from '../../gen/services/basic_types_pb.js';
import {CustomFeeLimitSchema, FixedFeeSchema} from '../../gen/services/custom_fees_pb.js';
import {RestError} from '../../errors';
import {CustomFeeLimits} from '../../model';

const serialize = (accountNum) =>
  Buffer.from(
    toBinary(
      CustomFeeLimitSchema,
      create(CustomFeeLimitSchema, {
        accountId: create(AccountIDSchema, {account: {case: 'accountNum', value: BigInt(accountNum)}}),
        fees: [create(FixedFeeSchema, {amount: 100n})],
      })
    )
  );

describe('CustomFeeLimits', () => {
  test.each([null, undefined, []])('empty input %p', (input) => {
    expect(new CustomFeeLimits(input).fees).toBeEmpty();
  });

  test('parses entries', () => {
    const fees = new CustomFeeLimits([serialize(8), serialize(9)]).fees;
    expect(fees.map((fee) => fee.accountId.account.value)).toEqual([8n, 9n]);
  });

  test('parses up to the maximum number of entries', () => {
    const input = Array.from({length: CustomFeeLimits.MAX_ENTRIES}, (_, i) => serialize(i + 1));
    expect(new CustomFeeLimits(input).fees).toHaveLength(CustomFeeLimits.MAX_ENTRIES);
  });

  test('truncates entries beyond the maximum', () => {
    const warnSpy = jest.spyOn(logger, 'warn').mockImplementation(() => {});
    try {
      const input = Array.from({length: CustomFeeLimits.MAX_ENTRIES + 5}, (_, i) => serialize(i + 1));
      const fees = new CustomFeeLimits(input).fees;
      expect(fees).toHaveLength(CustomFeeLimits.MAX_ENTRIES);
      expect(fees[CustomFeeLimits.MAX_ENTRIES - 1].accountId.account.value).toBe(BigInt(CustomFeeLimits.MAX_ENTRIES));
      expect(warnSpy).toHaveBeenCalledTimes(1);
    } finally {
      warnSpy.mockRestore();
    }
  });

  test.each([
    ['invalid wire type', Buffer.from([0xff])],
    ['truncated varint', Buffer.from([0x08, 0x80])],
    ['truncated length-delimited field', Buffer.from([0x0a, 0x05, 0x01])],
  ])('throws RestError on malformed entry: %s', (_, malformed) => {
    const parse = () => new CustomFeeLimits([serialize(8), malformed]);
    expect(parse).toThrow(RestError);
    expect(parse).toThrow('Failed to parse max_custom_fees entry at index 1');
  });
});
