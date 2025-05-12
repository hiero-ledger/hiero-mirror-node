// SPDX-License-Identifier: Apache-2.0

import fs from 'fs';
import path from 'path';

import {getModuleDirname} from '../../../testutils';

const recordFilename = '2021-03-05T05_23_04.299486999Z.rcd';
const recordFilePath = path.join(getModuleDirname(import.meta), recordFilename);
const data = fs.readFileSync(recordFilePath);

export default async () => {
  const fields = [
    'name',
    'load_start',
    'load_end',
    'hash',
    'prev_hash',
    'consensus_start',
    'consensus_end',
    'node_id',
    'count',
    'digest_algorithm',
    'hapi_version_major',
    'hapi_version_minor',
    'hapi_version_patch',
    'version',
    'file_hash',
    'bytes',
    'index',
  ];
  const placeholders = fields.map((_, index) => `$${index + 1}`).join(',');
  const query = [`insert into record_file (${fields.join(',')})`, `values (${placeholders})`].join('\n');
  const params = [
    recordFilename,
    1614921788,
    1614921788,
    'aae01bdff79eeacca460d84348d032b44c134c11f6c2d36d33f5a09e50c789957573be45c3eb81f7e90fa976c358a21b',
    '366fee025a8b2b2678096cf7c1becdfe6e6a3ec2b904ff054a4a4f010e8136dce8bbe4b0c63e4807a2e8d0570f479f89',
    '1614921784299486999',
    '1614921785873901000',
    0,
    8,
    0,
    0,
    11,
    0,
    5,
    'fe77e4b14de69068612543c59c1cfaf216a52e372fb5d5b27c3074bf08e2bf95645918b7059c3bc9498e42adb2dbc3c4',
    data,
    4085,
  ];
  await ownerPool.query(query, params);
};
