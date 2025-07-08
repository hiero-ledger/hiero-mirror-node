// SPDX-License-Identifier: Apache-2.0

import fs from 'fs';
import log4js from 'log4js';

import {TABLE_USAGE_OUTPUT_DIR} from './testutils.js';

const CSV_HEADER = 'Endpoint,Source,Table\n';
const MARKDOWN_ENDPOINT_HEADER = `### By Endpoint\n
| Endpoint | Tables |
|----------|--------|\n`;
const MARKDOWN_TABLE_HEADER = `\n### By Table\n
| Table | Endpoints |
|-------|-----------|\n`;
const REPORT_FILENAME = 'table-usage';

const createTableUsageReport = () => {
  if (process.env.GENERATE_TABLE_USAGE === 'false' || !fs.existsSync(TABLE_USAGE_OUTPUT_DIR)) {
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

  writeReport(tableUsage, `${REPORT_FILENAME}.csv`, writeCsvReport);
  writeReport(tableUsage, `${REPORT_FILENAME}.md`, writeMarkdownReport);
};

const writeCsvReport = (data, writeStream) => {
  writeStream.write(CSV_HEADER);
  for (const endpoint of Object.keys(data).sort()) {
    const callerTables = data[endpoint];
    for (const caller of Object.keys(callerTables).sort()) {
      for (const table of callerTables[caller]) {
        writeStream.write(`${endpoint},${caller},${table}\n`);
      }
    }
  }
};

const writeMarkdownReport = (data, writeStream) => {
  // By endpoint
  writeStream.write(MARKDOWN_ENDPOINT_HEADER);
  const usageByTable = {};
  for (const endpoint of Object.keys(data).sort()) {
    const callerTables = data[endpoint];
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
  writeStream.write(MARKDOWN_TABLE_HEADER);
  for (const table of Object.keys(usageByTable).sort()) {
    writeStream.write(`| ${table} | ${usageByTable[table].sort().join(',')} |\n`);
  }
};

const writeReport = (data, filename, writeData) => {
  const path = `${TABLE_USAGE_OUTPUT_DIR}/${filename}`;
  let writeStream;

  try {
    writeStream = fs.createWriteStream(path);
    writeData(data, writeStream);
  } catch (err) {
    console.error(`Unable to write file ${path} - ${err}`);
  } finally {
    writeStream?.close();
  }
};

export default async () => {
  createTableUsageReport();
  for (const dbContainer of globalThis.__DB_CONTAINERS__.values()) {
    await dbContainer.stop();
  }
  globalThis.__DB_CONTAINER_SERVER__.close();
  log4js.shutdown();
};
