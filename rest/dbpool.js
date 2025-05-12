// SPDX-License-Identifier: Apache-2.0

import config from './config.js';
import fs from 'fs';
import {getPoolClass} from './utils.js';

const poolConfig = {
  user: config.db.username,
  host: config.db.host,
  database: config.db.name,
  password: config.db.password,
  port: config.db.port,
  connectionTimeoutMillis: config.db.pool.connectionTimeout,
  max: config.db.pool.maxConnections,
  statement_timeout: config.db.pool.statementTimeout,
};

if (config.db.tls.enabled) {
  poolConfig.ssl = {
    ca: fs.readFileSync(config.db.tls.ca).toString(),
    cert: fs.readFileSync(config.db.tls.cert).toString(),
    key: fs.readFileSync(config.db.tls.key).toString(),
    rejectUnauthorized: false,
  };
}

const Pool = getPoolClass();

const handlePoolError = (dbPool) => {
  dbPool.on('error', (error) => {
    logger.error(`error event emitted on pool for host ${dbPool.options.host}. ${error.stack}`);
  });
};

const initializePool = () => {
  global.pool = new Pool(poolConfig);
  handlePoolError(global.pool);

  if (config.db.primaryHost) {
    const primaryPoolConfig = {...poolConfig};
    primaryPoolConfig.host = config.db.primaryHost;
    global.primaryPool = new Pool(primaryPoolConfig);
    handlePoolError(global.primaryPool);
  } else {
    global.primaryPool = pool;
  }
};

export {initializePool};
