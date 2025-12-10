// SPDX-License-Identifier: Apache-2.0

import {execSync} from 'child_process';
import fs from 'fs';
import os from 'os';
import path from 'path';

import config from '../config';
import {FLYWAY_DATA_PATH, FLYWAY_EXE_PATH, FLYWAY_VERSION} from './globalSetup';
import {getModuleDirname, isV2Schema} from './testutils';
import {getPoolClass} from '../utils';
import {GenericContainer, PullPolicy} from 'testcontainers';
import {RedisContainer} from '@testcontainers/redis';

const {db: defaultDbConfig} = config;
const Pool = getPoolClass();

const REDIS_IMAGE = 'redis:7.2';

const restJavaContainers = new Map();

const readOnlyUser = 'mirror_rest';
const readOnlyPassword = 'mirror_rest_pass';
const workerId = process.env.JEST_WORKER_ID;

const cleanupSql = fs.readFileSync(
  path.join(getModuleDirname(import.meta), '..', '..', 'common', 'src', 'test', 'resources', 'cleanup.sql'),
  'utf8'
);

const v1SchemaConfigs = {
  baselineVersion: '0',
  locations: '../importer/src/main/resources/db/migration/v1',
};
const v2SchemaConfigs = {
  baselineVersion: '1.999.999',
  locations: '../importer/src/main/resources/db/migration/v2',
};

const schemaConfigs = isV2Schema() ? v2SchemaConfigs : v1SchemaConfigs;

const cleanUp = async () => {
  await ownerPool.query(cleanupSql);
};

const createRestJavaContainer = async () => {
  const connectionParams = await getDbConnectionParams();
  await flywayMigrate(connectionParams);
  const bridgeHost = os.type() === 'Linux' ? '127.0.0.1' : 'host.docker.internal';
  return new GenericContainer('gcr.io/mirrornode/hedera-mirror-rest-java:latest')
    .withEnvironment({
      HEDERA_MIRROR_RESTJAVA_DB_HOST: bridgeHost,
      HEDERA_MIRROR_RESTJAVA_DB_PORT: connectionParams.port,
      HEDERA_MIRROR_RESTJAVA_DB_NAME: connectionParams.database,
    })
    .withExposedPorts(8084)
    .withPullPolicy(PullPolicy.defaultPolicy())
    .start();
};

/**
 * Gets connection parameters of the db container created for this jest worker
 *
 * @returns {Promise<{}>}
 */
const getDbConnectionParams = async () => {
  const response = await fetch(process.env.DB_CONTAINER_SERVER_URL + '/connectionParams', {
    method: 'POST',
    headers: {
      Connection: 'close', // close the connection to avoid ECONNRESET
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({workerId}),
  });

  return await response.json();
};

const initializeContainers = async () => {
  const connectionParams = await getDbConnectionParams();
  await flywayMigrate(connectionParams);
  await createPool(connectionParams);
};

const startRedisContainer = async () => new RedisContainer(REDIS_IMAGE).withStartupTimeout(20000).start();

const startRestJavaContainer = async () => {
  let container = restJavaContainers.get(workerId);
  if (!container) {
    container = await createRestJavaContainer();
    restJavaContainers.set(workerId, container);
  }

  global.REST_JAVA_BASE_URL = `http://${container.getHost()}:${container.getMappedPort(8084)}`;

  return container;
};

const createPool = async (connectionParams) => {
  global.ownerPool = new Pool(connectionParams);
  global.pool = new Pool({
    ...connectionParams,
    password: readOnlyPassword,
    user: readOnlyUser,
  });
  global.primaryPool = global.pool;
};

/**
 * Run the SQL (non-java) based migrations stored in the Importer project against the target database.
 */
const flywayMigrate = async (connectionParams) => {
  const {database, host, password, port, user} = connectionParams;
  logger.info(`Using flyway CLI to construct schema for jest worker ${workerId} on port ${port}`);
  const jdbcUrl = `jdbc:postgresql://${host}:${port}/${database}`;
  const flywayConfigPath = path.join(FLYWAY_DATA_PATH, `config_worker_${workerId}.json`);
  const locations = getMigrationScriptLocation(schemaConfigs.locations);

  const flywayConfigObject = {
    flywayArgs: {
      baselineOnMigrate: 'true',
      baselineVersion: schemaConfigs.baselineVersion,
      locations: `filesystem:${locations}`,
      password: password,
      'placeholders.api-password': defaultDbConfig.password,
      'placeholders.api-user': defaultDbConfig.username,
      'placeholders.balancePartitionTimeInterval': "'10 years'",
      'placeholders.db-name': database,
      'placeholders.db-user': user,
      'placeholders.hashShardCount': 2,
      'placeholders.partitionStartDate': "'1970-01-01'",
      'placeholders.partitionTimeInterval': "'10 years'",
      'placeholders.topicRunningHashV2AddedTimestamp': 0,
      'placeholders.transactionHashLookbackInterval': "'60 days'",
      'placeholders.schema': 'public',
      'placeholders.shardCount': 2,
      'placeholders.tempSchema': 'temporary',
      target: 'latest',
      url: jdbcUrl,
      user: user,
    },
    version: FLYWAY_VERSION,
    downloads: {
      storageDirectory: FLYWAY_DATA_PATH,
    },
  };

  const flywayConfig = JSON.stringify(flywayConfigObject, null, 2);

  fs.writeFileSync(flywayConfigPath, flywayConfig);
  logger.info(`Added ${flywayConfigPath} to file system for flyway CLI`);

  const maxRetries = 10;
  let retries = maxRetries;
  const retryMsDelay = 2000;

  while (retries-- > 0) {
    try {
      execSync(`node ${FLYWAY_EXE_PATH} -c "${flywayConfigPath}" migrate`, {stdio: 'inherit'});
      logger.info(`Successfully executed all Flyway migrations for jest worker ${workerId}`);
      break;
    } catch (e) {
      logger.warn(`Error running flyway for jest worker ${workerId} during attempt #${maxRetries - retries}: ${e}`);
      await new Promise((resolve) => setTimeout(resolve, retryMsDelay));
    }
  }

  fs.rmSync(locations, {force: true, recursive: true});

  if (retries < 0) {
    throw new Error('Failed to run flyway migrate');
  }
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
  initializeContainers,
  startRedisContainer,
  startRestJavaContainer,
};
