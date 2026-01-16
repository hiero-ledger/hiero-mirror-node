// SPDX-License-Identifier: Apache-2.0

import fs from 'fs';
import os from 'os';
import path from 'path';
import {ReportFile} from '../src/reportfile.js';

let outputFile;
let reportFile;

beforeEach(() => {
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'mirror-report-'));
  outputFile = path.join(tempDir, 'report.csv');
  reportFile = new ReportFile();
});

afterEach(() => {
  fs.rmdirSync(path.dirname(outputFile), {force: true, recursive: true});
});

describe('ReportFile', () => {
  test('No data', async () => {
    reportFile.write(outputFile);
    expect(fs.existsSync(outputFile)).toBeFalsy();
  });

  test('Has data', async () => {
    const line = 'line1\n';
    reportFile.append(line);
    reportFile.write(outputFile);
    const data = fs.readFileSync(outputFile, 'utf8');
    expect(data).toBe(ReportFile.HEADER + line);
  });
});
