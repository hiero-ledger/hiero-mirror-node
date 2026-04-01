// SPDX-License-Identifier: Apache-2.0

import {extract} from 'tar';
import {createGunzip} from 'node:zlib';
import {pipeline} from 'node:stream/promises';
import express from 'express';
import fs from 'fs';
import path from 'path';
import {PostgreSqlContainer} from '@testcontainers/postgresql';

import {isV2Schema} from './testutils.js';

const FLYWAY_DATA_PATH = path.join('.', 'build', process.platform === 'win32' ? 'flyway data' : 'flyway');
const FLYWAY_VERSION = '12.0.3';
const FLYWAY_EXE_PATH = path.join('.', 'node_modules', 'flyway', FLYWAY_VERSION, 'flyway');

const DB_NAME = 'mirror_node';
const OWNER_USER = 'mirror_node';
const DATABASE_IMAGES = {
  v1: 'gcr.io/mirrornode/postgres:16-alpine',
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

const initializeFlyway = async () => {
  if (fs.existsSync(FLYWAY_EXE_PATH)) {
    console.info(`Found existing Flyway CLI at ${FLYWAY_EXE_PATH}`);
    return;
  }

  const platformMap = {
    darwin: 'macosx-arm64.tar.gz',
    linux: 'linux-x64.tar.gz',
    win32: 'windows-x64.zip',
  };

  const destDir = path.dirname(FLYWAY_EXE_PATH);
  fs.mkdirSync(destDir, {recursive: true});

  const suffix = platformMap[process.platform];
  const url = `https://github.com/flyway/flyway/releases/download/flyway-${FLYWAY_VERSION}/flyway-commandline-${FLYWAY_VERSION}-${suffix}`;
  console.info(`Downloading Flyway from ${url}`);

  for (let i = 0; i < 10; i++) {
    try {
      const response = await fetch(url);

      if (response.ok) {
        await pipeline(response.body, createGunzip(), extract({cwd: destDir, strip: 1}));
        console.info(`Downloaded Flyway CLI to ${FLYWAY_EXE_PATH}`);
        return;
      }

      console.warn(`Failed to download ${url}: ${response.status} ${response.statusText}`);
      await new Promise((r) => setTimeout(r, 1000));
    } catch (err) {
      console.warn(`Failed to download ${url}: ${err}`);
      await new Promise((r) => setTimeout(r, 1000));
    }
  }

  throw new Error('Failed to initialize flyway');
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

export default async () => {
  await initializeFlyway();
  startDbContainerServer();
};

export {FLYWAY_DATA_PATH, FLYWAY_EXE_PATH, FLYWAY_VERSION};
