// SPDX-License-Identifier: Apache-2.0

import fs from 'fs';
import log4js from 'log4js';

import {TABLE_USAGE_OUTPUT_DIR} from './testutils.js';

const CSV_HEADER = 'Endpoint,Source,Table\n';
const MARKDOWN_ENDPOINT_HEADER = `| Endpoint | Tables |
|----------|--------|\n`;
const MARKDOWN_TABLE_HEADER = `| Table | Endpoints |
|-------|-----------|\n`;
const REPORT_FILENAME = 'table-usage';

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

  writeCsvReport(tableUsage);
  writeMarkdownReport(tableUsage);
};

const writeCsvReport = (tableUsage) => {
  const writeStream = fs.createWriteStream(`${TABLE_USAGE_OUTPUT_DIR}/${REPORT_FILENAME}.csv`);
  writeStream.write(CSV_HEADER);

  for (const endpoint of Object.keys(tableUsage).sort()) {
    const callerTables = tableUsage[endpoint];
    for (const caller of Object.keys(callerTables).sort()) {
      for (const table of callerTables[caller]) {
        writeStream.write(`${endpoint},${caller},${table}\n`);
      }
    }
  }

  writeStream.close();
};

const writeMarkdownReport = (tableUsage) => {
  const writeStream = fs.createWriteStream(`${TABLE_USAGE_OUTPUT_DIR}/${REPORT_FILENAME}.md`);

  // By endpoint
  writeStream.write('### By Endpoint\n\n');
  writeStream.write(MARKDOWN_ENDPOINT_HEADER);

  const usageByTable = {};
  for (const endpoint of Object.keys(tableUsage).sort()) {
    const callerTables = tableUsage[endpoint];
    const tables = Array.from(new Set(Object.values(callerTables).flat())).sort();
    writeStream.write(`| ${endpoint} | ${tables.join(',')}|\n`);

    tables.forEach((table) => {
      if (!table) {
        return;
      }

      (usageByTable[table] ?? (usageByTable[table] = [])).push(endpoint);
    });
  }

  // By table
  writeStream.write('\n### By Table\n\n');
  writeStream.write(MARKDOWN_TABLE_HEADER);

  for (const table of Object.keys(usageByTable).sort()) {
    writeStream.write(`| ${table} | ${usageByTable[table].sort().join(',')} |\n`);
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
