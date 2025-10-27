// SPDX-License-Identifier: Apache-2.0

import {execSync} from 'child_process';
import express from 'express';
import fs from 'fs';
import path from 'path';
import {PostgreSqlContainer} from '@testcontainers/postgresql';

import {isV2Schema} from './testutils.js';

const FLYWAY_DATA_PATH = path.join('.', 'build', process.platform === 'win32' ? 'flyway data' : 'flyway');
const FLYWAY_EXE_PATH = path.join('.', 'node_modules', 'node-flywaydb', 'bin', 'flyway');
const FLYWAY_VERSION = '11.8.2';

const DB_NAME = 'mirror_node';
const OWNER_USER = 'mirror_node';
const DATABASE_IMAGES = {
  v1: 'postgres:16-alpine',
  v2: 'gcr.io/mirrornode/citus:12.1.1',
};

const dbContainers = new Map();

const createDbContainer = async (workerId) => {
  const image = isV2Schema() ? DATABASE_IMAGES.v2 : DATABASE_IMAGES.v1;
  const initSqlPath = path.join('..', 'common', 'src', 'test', 'resources', 'init.sql');
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
        .withDatabase(DB_NAME)
        .withUsername(OWNER_USER)
        .start();
      console.info(`Started PostgreSQL container for worker ${workerId} with image ${image}`);
      return container;
    } catch (e) {
      console.warn(
        `Error starting PostgreSQL container for worker ${workerId} during attempt #${maxRetries - retries}: ${e}`
      );
      await new Promise((resolve) => setTimeout(resolve, retryMsDelay));
    }
  }

  throw new Error(`Unable to start PostgreSQL container for worker ${workerId} after all attempts`);
};

const initializeFlyway = () => {
  const flywayConfigPath = path.join(FLYWAY_DATA_PATH, `config_global.json`);
  const flywayConfig = {
    flywayArgs: {
      url: 'jdbc:postgresql://127.0.0.1:-1/invalid',
    },
    version: FLYWAY_VERSION,
    downloads: {
      storageDirectory: FLYWAY_DATA_PATH,
    },
  };

  fs.mkdirSync(FLYWAY_DATA_PATH, {recursive: true});
  fs.writeFileSync(flywayConfigPath, JSON.stringify(flywayConfig));
  const command = `node ${FLYWAY_EXE_PATH} -c "${flywayConfigPath}" info`;
  const options = {stdio: 'pipe'};

  let retries = 10;
  while (retries-- > 0) {
    try {
      execSync(command, options);
      break;
    } catch (e) {
      const errMessage = e.stderr.toString();
      if (errMessage.includes('-1 not valid')) {
        console.warn(e.stdout.toString());
        break;
      } else {
        console.warn(errMessage);
      }
    }
  }

  if (retries < 0) {
    throw new Error('Failed to initialize flyway');
  }
};

const startDbContainerServer = () => {
  const app = express();
  app.use(express.json()); // process POST json body
  const server = app.listen();
  globalThis.__DB_CONTAINER_SERVER__ = server;
  globalThis.__DB_CONTAINERS__ = dbContainers;
  process.env.DB_CONTAINER_SERVER_URL = `http://localhost:${server.address().port}`;

  app.post('/connectionParams', async (req, res) => {
    if (Number.isNaN(req.body?.workerId)) {
      res.status(400).end();
      return;
    }

    const workerId = Number(req.body.workerId);
    let dbContainer = dbContainers.get(workerId);
    if (!dbContainer) {
      dbContainer = await createDbContainer(workerId);
      dbContainers.set(workerId, dbContainer);
    }

    res.status(200).json({
      database: dbContainer.getDatabase(),
      host: dbContainer.getHost(),
      password: dbContainer.getPassword(),
      port: dbContainer.getPort(),
      sslmode: 'DISABLE',
      user: dbContainer.getUsername(),
    });
  });
};

export default () => {
  initializeFlyway();
  startDbContainerServer();
};

export {FLYWAY_DATA_PATH, FLYWAY_EXE_PATH, FLYWAY_VERSION};
