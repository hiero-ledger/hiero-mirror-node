// SPDX-License-Identifier: Apache-2.0

import HashObject from '../../stream/hashObject';
import {calculateRunningHash} from '../../stream/runningHash';

test('calculateRunningHash', () => {
  const header = Buffer.from([0xde, 0xad, 0xbe, 0xef]);
  const runningHashObject = {
    header,
    hash: Buffer.from(new Array(48).fill(0xab)),
  };
  const nextHashObject = {
    header,
    hash: Buffer.from(new Array(48).fill(0xef)),
  };
  const expected = Buffer.from('mIgXt6GDsWO8SQYe78oWPrPFRlfX0H+6+cn7Smn19ByLOjwd+J3GQCPRyPG1cuQw', 'base64');

  expect(calculateRunningHash(runningHashObject, nextHashObject, HashObject.SHA_384.name)).toEqual(expected);
});
