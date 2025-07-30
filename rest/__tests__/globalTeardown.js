// SPDX-License-Identifier: Apache-2.0

import fs from 'fs';
import log4js from 'log4js';

import {TABLE_USAGE_OUTPUT_DIR} from './testutils.js';
import {EOL} from 'os';

const BR = '<br>';
const CSV_HEADER = `endpoint,table,source${EOL}`;
const MARKDOWN_ENDPOINT_HEADER = `# Table Usage Report${EOL}${EOL}## By Endpoint${EOL}${EOL}| Endpoint | Tables | Sources |${EOL}|----------|--------|---------|${EOL}`;
const MARKDOWN_TABLE_HEADER = `${EOL}## By Table${EOL}${EOL}| Table | Endpoints | Sources |${EOL}|-------|---------|-----------|${EOL}`;
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
        writeStream.write(`${endpoint},${table},${caller}${EOL}`);
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
    const sources = Object.keys(callerTables).sort();
    const tables = Array.from(new Set(Object.values(callerTables).flat())).sort();
    writeStream.write(`| ${endpoint} | ${tables.join(BR)}|${sources.join(BR)}|${EOL}`);

    Object.entries(callerTables).forEach(([caller, table]) => {
      table.forEach((t) => {
        const entry = usageByTable[t] ?? (usageByTable[t] = {endpoints: new Set(), sources: new Set()});
        entry.endpoints.add(endpoint);
        entry.sources.add(caller);
      });
    });
  }

  // By table
  writeStream.write(MARKDOWN_TABLE_HEADER);
  for (const table of Object.keys(usageByTable).sort()) {
    const endpoints = Array.from(usageByTable[table].endpoints).sort().join(BR);
    const sources = Array.from(usageByTable[table].sources).sort().join(BR);
    writeStream.write(`| ${table} | ${endpoints} | ${sources} |${EOL}`);
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
