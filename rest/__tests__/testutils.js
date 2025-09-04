// SPDX-License-Identifier: Apache-2.0

import path from 'path';
import {fileURLToPath} from 'url';

const invalidBase32Strs = [
  // A base32 group without padding can have 2, 4, 5, 7 or 8 characters from its alphabet
  '',
  'A',
  'AAA',
  'AAAAAA',
  // non-base32 characters
  '00',
  '11',
  '88',
  '99',
  'aa',
  'AA======', // padding not accepted
];

const TABLE_USAGE_OUTPUT_DIR = 'build/reports/tableusage';

const assertSqlQueryEqual = (actual, expected) => {
  expect(formatSqlQueryString(actual)).toEqual(formatSqlQueryString(expected));
};

const formatSqlQueryString = (query) => {
  return query
    .trim()
    .replace(/\n/g, ' ')
    .replace(/\(\s+/g, '(')
    .replace(/\s+\)/g, ')')
    .replace(/\s+/g, ' ')
    .replace(/\s*,\s+/g, ',')
    .toLowerCase();
};

const getAllAccountAliases = (alias) => [alias, `0.${alias}`, `0.0.${alias}`];

const getModuleDirname = (importMeta) => path.dirname(fileURLToPath(importMeta.url));

const isV2Schema = () => process.env.MIRROR_NODE_SCHEMA === 'v2';

const hexRegex = /^(0x)?[0-9A-Fa-f]+$/;

const valueToBuffer = (value) => {
  if (value === null) {
    return value;
  }

  if (typeof value === 'string') {
    if (hexRegex.test(value)) {
      return Buffer.from(value.replace(/^0x/, '').padStart(2, '0'), 'hex');
    }

    // base64
    return Buffer.from(value, 'base64');
  } else if (Array.isArray(value)) {
    return Buffer.from(value);
  }

  return value;
};

export {
  assertSqlQueryEqual,
  formatSqlQueryString,
  getAllAccountAliases,
  getModuleDirname,
  invalidBase32Strs,
  isV2Schema,
  valueToBuffer,
  TABLE_USAGE_OUTPUT_DIR,
};
