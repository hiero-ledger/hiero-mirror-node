// SPDX-License-Identifier: Apache-2.0

import fs from 'fs';
import log4js from 'log4js';

import {TABLE_USAGE_OUTPUT_DIR} from './testutils.js';

const HEADER = `| Endpoint | Functions | Tables |
|----------|--------|--------|`;
const REPORT_FILENAME = 'table-usage.md';

const createTableUsageReport = () => {
  if (process.env.NO_GENERATE_TABLE_USAGE === 'true' || !fs.existsSync(TABLE_USAGE_OUTPUT_DIR)) {
    return;
  }

  const tableUsage = {};
  for (const dirent of fs.readdirSync(TABLE_USAGE_OUTPUT_DIR, {withFileTypes: true})) {
    if (!dirent.isFile() || !dirent.name.endsWith('.json')) {
      continue;
    }

    try {
      Object.assign(
        tableUsage,
        JSON.parse(fs.readFileSync(`${TABLE_USAGE_OUTPUT_DIR}/${dirent.name}`, {encoding: 'utf8'}))
      );
    } catch (err) {
      console.error(`Unable to read file ${dirent.name} - ${err}`);
    }
  }

  const writeStream = fs.createWriteStream(`${TABLE_USAGE_OUTPUT_DIR}/${REPORT_FILENAME}`);
  writeStream.write(`${HEADER}\n`);

  for (const endpoint of Object.keys(tableUsage).sort()) {
    const callerTables = tableUsage[endpoint];
    const callers = Object.keys(callerTables).sort();
    const functions = callers.map((caller) => callerTables[caller]);
    writeStream.write(`| ${endpoint} | ${callers.join('<br>')} | ${functions.join('<br>')} |\n`);
  }

  writeStream.close();
};

export default async () => {
  createTableUsageReport();
  for (const dbContainer of globalThis.__DB_CONTAINERS__.values()) {
    await dbContainer.stop();
  }
  globalThis.__DB_CONTAINER_SERVER__.close();
  log4js.shutdown();
};
