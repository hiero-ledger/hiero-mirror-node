// SPDX-License-Identifier: Apache-2.0

import fs from 'fs';
import path from 'path';

import {getModuleDirname} from '../testutils';

const mark = '$$GROUP_SPEC_PATH$$';

const dirname = getModuleDirname(import.meta);
const specsPath = path.join(dirname, '..', 'specs');
const template = fs.readFileSync(path.join(dirname, 'template.js')).toString();

// Remove any generated spec tests
fs.readdirSync(dirname, {withFileTypes: true})
  .filter((f) => f.isFile() && f.name.endsWith('.spec.test.js'))
  .forEach((f) => fs.rmSync(path.join(f.parentPath, f.name)));

fs.readdirSync(specsPath, {withFileTypes: true})
  .filter((dirent) => dirent.isDirectory())
  .forEach((dirent) => {
    const group = dirent.name;
    const filename = path.join(dirname, `${group}.spec.test.js`);
    fs.writeFileSync(filename, template.replace(mark, `'${group}'`));
  });
