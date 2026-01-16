// SPDX-License-Identifier: Apache-2.0

import {expect} from '@jest/globals';
import fs from 'fs';
import {parse} from 'pgsql-parser';

import {apiPrefix} from '../constants';
import {TABLE_USAGE_OUTPUT_DIR} from './testutils';

const TABLE_EXISTS_SQL = `select table_name
  from information_schema.tables
  where table_schema = 'public' and table_name = any($1)`;

const allQueries = {}; // SQL queries by endpoint, then by caller
const enabled = process.env.GENERATE_TABLE_USAGE !== 'false';

const getEndpoint = () => {
  const {currentTestName} = expect.getState();
  const extracted = (currentTestName.match(/^API specification tests - (.*) [^ ]*\.json.*$/) ?? [])[1];
  if (!extracted) {
    return null;
  }

  return `${apiPrefix}/${extracted.replaceAll(' ', '')}`;
};

/**
 * Gets possible tables accessed by the PostgreSQL query
 * @param node - The AST node
 * @returns [string] - An array of tables extracted from the PostgreSQL query AST
 */
const getTablesForNode = (node) => {
  if (Array.isArray(node)) {
    return node.map(getTablesForNode).flat();
  } else if (typeof node === 'object') {
    if ('RangeVar' in node) {
      return node['RangeVar'].relname;
    } else {
      return Object.values(node).map(getTablesForNode).flat();
    }
  }

  return [];
};

const getTablesForQuery = async (query) => {
  try {
    // the result from the parser will also include non tables, e.g., named CTE used as a from item
    const {stmts} = await parse(query);
    return getTablesForNode(stmts);
  } catch (err) {
    logger.error(err);
    return new Set();
  }
};

const recordQuery = (callerInfo, query) => {
  if (!shouldTrackTableUsage(callerInfo.path)) {
    return;
  }

  const caller = `${callerInfo.function} (${callerInfo.file}:${callerInfo.line})`;
  const endpoint = getEndpoint();
  const callerQueries = allQueries[endpoint] ?? (allQueries[endpoint] = {});
  const queries = callerQueries[caller] ?? (callerQueries[caller] = []);
  queries.push(query);
};

const shouldTrackTableUsage = (callerFilePath) => {
  if (!enabled || !callerFilePath || callerFilePath.includes('__tests__')) {
    return false;
  }

  const {testPath} = expect.getState();
  return testPath.endsWith('.spec.test.js') && !testPath.endsWith('route.spec.test.js');
};

const writeTableUsage = async (group) => {
  if (Object.keys(allQueries).length === 0) {
    return;
  }

  // two passes, first parses all queries and collect possible tables
  let possibleTables = new Set();
  const usage = {};
  for (const [endpoint, callerQueries] of Object.entries(allQueries)) {
    const callerTables = {};
    for (const [caller, queries] of Object.entries(callerQueries)) {
      const tables = [];
      for (const query of queries) {
        tables.push(...(await getTablesForQuery(query)));
      }

      callerTables[caller] = new Set(tables);
      possibleTables = possibleTables.union(callerTables[caller]);
    }

    usage[endpoint] = callerTables;
  }

  // query to find which are tables
  const knownTables = new Set();
  const tables = Array.from(possibleTables);
  let i = 0;
  for (; i < tables.length; i += 512) {
    const end = Math.min(i + 512, tables.length);
    const {rows} = await pool.queryQuietly(TABLE_EXISTS_SQL, [tables.slice(i, end)]);
    rows.forEach(({table_name}) => knownTables.add(table_name));
  }

  // remove non tables
  for (const callerTables of Object.values(usage)) {
    for (const caller of Object.keys(callerTables)) {
      callerTables[caller] = Array.from(callerTables[caller].intersection(knownTables)).sort();
    }
  }

  fs.mkdirSync(TABLE_USAGE_OUTPUT_DIR, {recursive: true});
  fs.writeFileSync(`${TABLE_USAGE_OUTPUT_DIR}/${group}.json`, JSON.stringify(usage));
};

export {recordQuery, writeTableUsage};
