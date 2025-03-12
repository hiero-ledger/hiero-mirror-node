// SPDX-License-Identifier: Apache-2.0

import {execSync} from 'child_process';
import fs from 'fs';
import os from 'os';
import path from 'path';

import config from '../config';
import {getModuleDirname, isV2Schema} from './testutils';
import {getPoolClass} from '../utils';
import {PostgreSqlContainer} from '@testcontainers/postgresql';

const {db: defaultDbConfig} = config;
const Pool = getPoolClass();

const containers = new Map();
const dbName = 'mirror_node';
const ownerUser = 'mirror_node';
const ownerPassword = 'mirror_node_pass';
const readOnlyUser = 'mirror_rest';
const readOnlyPassword = 'mirror_rest_pass';
const workerId = process.env.JEST_WORKER_ID;
const v1DatabaseImage = 'postgres:16-alpine';
const v2DatabaseImage = 'gcr.io/mirrornode/citus:12.1.1';

const cleanupSql = fs.readFileSync(
  path.join(
    getModuleDirname(import.meta),
    '..',
    '..',
    'hedera-mirror-common',
    'src',
    'test',
    'resources',
    'cleanup.sql'
  ),
  'utf8'
);

const v1SchemaConfigs = {
  baselineVersion: '0',
  locations: '../hedera-mirror-importer/src/main/resources/db/migration/v1',
};
const v2SchemaConfigs = {
  baselineVersion: '1.999.999',
  locations: '../hedera-mirror-importer/src/main/resources/db/migration/v2',
};

const schemaConfigs = isV2Schema() ? v2SchemaConfigs : v1SchemaConfigs;

const cleanUp = async () => {
  await ownerPool.query(cleanupSql);
};

const createDbContainer = async () => {
  const image = isV2Schema() ? v2DatabaseImage : v1DatabaseImage;
  const initSqlPath = path.join('..', 'hedera-mirror-common', 'src', 'test', 'resources', 'init.sql');
  const initSqlCopy = {
    source: initSqlPath,
    target: '/docker-entrypoint-initdb.d/init.sql',
  };

  const maxRetries = 10;
  let retries = maxRetries;
  const retryMsDelay = 2000;
  let container;

  while (retries-- > 0) {
    try {
      container = await new PostgreSqlContainer(image)
        .withCopyFilesToContainer([initSqlCopy])
        .withDatabase(dbName)
        .withPassword(ownerPassword)
        .withUsername(ownerUser)
        .start();
      break;
    } catch (e) {
      logger.warn(`Error start PostgreSQL container worker ${workerId} during attempt #${maxRetries - retries}: ${e}`);
      await new Promise((resolve) => setTimeout(resolve, retryMsDelay));
    }
  }

  logger.info(`Started PostgreSQL container for jest worker ${workerId} with image ${image}`);
  await flywayMigrate(container);
  return container;
};

/**
 * Gets the port of the container in use by the Jest worker. If the container does not exist, a new container is created
 *
 * @returns {Promise<PostgreSqlContainer>}
 */
const getDbContainer = async () => {
  let container = containers.get(workerId);

  if (!container) {
    container = await createDbContainer();
    containers.set(workerId, container);
  }

  return container;
};

const createPool = async () => {
  const container = await getDbContainer();
  const dbConnectionParams = {
    database: container.getDatabase(),
    host: container.getHost(),
    password: container.getPassword(),
    port: container.getPort(),
    sslmode: 'DISABLE',
    user: container.getUsername(),
  };

  global.ownerPool = new Pool(dbConnectionParams);
  global.pool = new Pool({
    ...dbConnectionParams,
    password: readOnlyPassword,
    user: readOnlyUser,
  });
  global.primaryPool = global.pool;
};

/**
 * Run the SQL (non-java) based migrations stored in the Importer project against the target database.
 */
const flywayMigrate = async (container) => {
  const containerPort = container.getPort();
  logger.info(`Using flyway CLI to construct schema for jest worker ${workerId} on port ${containerPort}`);
  const jdbcUrl = `jdbc:postgresql://localhost:${containerPort}/${dbName}`;
  const exePath = path.join('.', 'node_modules', 'node-flywaydb', 'bin', 'flyway');
  const flywayDataPath = path.join('.', 'build', `${workerId}`, 'flyway');
  const flywayConfigPath = path.join(os.tmpdir(), `config_worker_${workerId}.json`); // store configs in temp dir
  const locations = getMigrationScriptLocation(schemaConfigs.locations);

  const flywayConfig = `{
    "flywayArgs": {
      "baselineOnMigrate": "true",
      "baselineVersion": "${schemaConfigs.baselineVersion}",
      "locations": "filesystem:${locations}",
      "password": "${ownerPassword}",
      "placeholders.api-password": "${defaultDbConfig.password}",
      "placeholders.api-user": "${defaultDbConfig.user}",
      "placeholders.db-name": "${dbName}",
      "placeholders.db-user": "${ownerUser}",
      "placeholders.hashShardCount": 2,
      "placeholders.partitionStartDate": "'1970-01-01'",
      "placeholders.partitionTimeInterval": "'10 years'",
      "placeholders.topicRunningHashV2AddedTimestamp": 0,
      "placeholders.schema": "public",
      "placeholders.shardCount": 2,
      "placeholders.tempSchema": "temporary",
      "target": "latest",
      "url": "${jdbcUrl}",
      "user": "${ownerUser}"
    },
    "version": "9.22.3",
    "downloads": {
      "storageDirectory": "${flywayDataPath}"
    }
  }`;

  fs.mkdirSync(flywayDataPath, {recursive: true});
  fs.writeFileSync(flywayConfigPath, flywayConfig);
  logger.info(`Added ${flywayConfigPath} to file system for flyway CLI`);

  const maxRetries = 10;
  let retries = maxRetries;
  const retryMsDelay = 2000;

  while (retries-- > 0) {
    try {
      execSync(`node ${exePath} -c ${flywayConfigPath} migrate`, {stdio: 'inherit'});
      logger.info(`Successfully executed all Flyway migrations for jest worker ${workerId}`);
      break;
    } catch (e) {
      logger.warn(`Error running flyway for jest worker ${workerId} during attempt #${maxRetries - retries}: ${e}`);
      await new Promise((resolve) => setTimeout(resolve, retryMsDelay));
    }
  }

  fs.rmSync(locations, {force: true, recursive: true});
};

const getMigrationScriptLocation = (locations) => {
  // Creating a temp directory for v2, without the repeatable partitioning file.
  const dest = fs.mkdtempSync(path.join(os.tmpdir(), 'migration-scripts-'));
  const ignoredMigrations = ['R__01_temp_tables.sql', 'R__02_temp_table_distribution.sql'];
  logger.info(`Created temp directory for v2 migration scripts - ${dest}`);
  fs.readdirSync(locations)
    .filter((filename) => ignoredMigrations.indexOf(filename) === -1)
    .forEach((filename) => {
      const srcFile = path.join(locations, filename);
      const dstFile = path.join(dest, filename);
      fs.copyFileSync(srcFile, dstFile);
    });

  return dest;
};

export default {
  cleanUp,
  createPool,
};
