// SPDX-License-Identifier: Apache-2.0

// external libraries
import express from 'express';

import {createTerminus} from '@godaddy/terminus';
import {addAsync} from '@awaitjs/express';
import cors from 'cors';
import httpContext from 'express-http-context';
import compression from 'compression';

// local files
import accounts from './accounts.js';
import balances from './balances.js';
import config from './config.js';
import * as constants from './constants.js';
import health from './health.js';
import schedules from './schedules.js';
import stateproof from './stateproof.js';
import tokens from './tokens.js';
import topicmessage from './topicmessage.js';
import transactions from './transactions.js';
import {isTestEnv} from './utils.js';

// import {handleError} from './middleware';
// import {metricsHandler} from './middleware';
// import {openApiValidator} from './middleware';
// import {recordIpAndEndpoint} from './middleware';
// import {requestLogger} from './middleware';
// import {requestQueryParser} from './middleware';
// import {responseCacheCheckHandler} from './middleware';
// import {responseCacheUpdateHandler} from './middleware';
// import {responseHandler} from './middleware';
// import {serveSwaggerDocs} from './middleware';


import {
  handleError,
  metricsHandler,
  openApiValidator,
  recordIpAndEndpoint,
  requestLogger,
  requestQueryParser,
  responseCacheCheckHandler,
  responseCacheUpdateHandler,
  responseHandler,
  serveSwaggerDocs,
} from './middleware/index.js';

// routes
import {AccountRoutes, BlockRoutes, ContractRoutes, NetworkRoutes} from './routes/index.js';
import {handleRejection, handleUncaughtException} from './middleware/httpErrorHandler.js';
import {initializePool} from './dbpool.js';

// use a dummy port for jest unit tests
const port = isTestEnv() ? 3000 : config.port;
if (port === undefined || Number.isNaN(Number(port))) {
  logger.error('Server started with unknown port');
  process.exit(1);
}

// Postgres pool
initializePool();

// Express configuration. Prior to v0.5 all sets should be configured before use or they won't be picked up
const app = addAsync(express());
const {apiPrefix} = constants;
const applicationCacheEnabled = config.cache.response.enabled && config.redis.enabled;
const openApiValidatorEnabled = config.openapi.validation.enabled;

app.disable('x-powered-by');
app.set('trust proxy', true);
app.set('port', port);
app.set('query parser', requestQueryParser);

serveSwaggerDocs(app);
if (openApiValidatorEnabled || isTestEnv()) {
  openApiValidator(app);
}

// middleware functions, Prior to v0.5 define after sets
app.use(
  express.urlencoded({
    extended: false,
  })
);
app.use(express.json());
app.use(cors());

if (config.response.compression) {
  logger.info('Response compression is enabled');
  app.use(compression());
}

// logging middleware
app.use(httpContext.middleware);
app.useAsync(requestLogger);

// metrics middleware
if (config.metrics.enabled) {
  app.use(metricsHandler());
}

// Check for cached response
if (applicationCacheEnabled) {
  logger.info('Response caching is enabled');
  app.useAsync(responseCacheCheckHandler);
}

// accounts routes
app.getAsync(`${apiPrefix}/accounts`, accounts.getAccounts);
app.getAsync(`${apiPrefix}/accounts/:${constants.filterKeys.ID_OR_ALIAS_OR_EVM_ADDRESS}`, accounts.getOneAccount);
app.useAsync(`${apiPrefix}/${AccountRoutes.resource}`, AccountRoutes.router);

// balances routes
app.getAsync(`${apiPrefix}/balances`, balances.getBalances);

// contracts routes
app.useAsync(`${apiPrefix}/${ContractRoutes.resource}`, ContractRoutes.router);

// network routes
app.useAsync(`${apiPrefix}/${NetworkRoutes.resource}`, NetworkRoutes.router);

// block routes
app.useAsync(`${apiPrefix}/${BlockRoutes.resource}`, BlockRoutes.router);

// schedules routes
app.getAsync(`${apiPrefix}/schedules`, schedules.getSchedules);
app.getAsync(`${apiPrefix}/schedules/:scheduleId`, schedules.getScheduleById);

// stateproof route
if (config.stateproof.enabled || isTestEnv()) {
  logger.info('stateproof REST API is enabled, install handler');
  app.getAsync(`${apiPrefix}/transactions/:transactionId/stateproof`, stateproof.getStateProofForTransaction);
} else {
  logger.info('stateproof REST API is disabled');
}

// tokens routes
app.getAsync(`${apiPrefix}/tokens`, tokens.getTokensRequest);
app.getAsync(`${apiPrefix}/tokens/:tokenId`, tokens.getTokenInfoRequest);
app.getAsync(`${apiPrefix}/tokens/:tokenId/balances`, tokens.getTokenBalances);
app.getAsync(`${apiPrefix}/tokens/:tokenId/nfts`, tokens.getNftTokensRequest);
app.getAsync(`${apiPrefix}/tokens/:tokenId/nfts/:serialNumber`, tokens.getNftTokenInfoRequest);
app.getAsync(`${apiPrefix}/tokens/:tokenId/nfts/:serialNumber/transactions`, tokens.getNftTransferHistoryRequest);

// topics routes
app.getAsync(`${apiPrefix}/topics/:topicId/messages`, topicmessage.getTopicMessages);
app.getAsync(`${apiPrefix}/topics/:topicId/messages/:sequenceNumber`, topicmessage.getMessageByTopicAndSequenceRequest);
app.getAsync(`${apiPrefix}/topics/messages/:consensusTimestamp`, topicmessage.getMessageByConsensusTimestamp);

// transactions routes
app.getAsync(`${apiPrefix}/transactions`, transactions.getTransactions);
app.getAsync(`${apiPrefix}/transactions/:transactionIdOrHash`, transactions.getTransactionsByIdOrHash);

// record ip metrics if enabled
if (config.metrics.ipMetrics) {
  app.useAsync(recordIpAndEndpoint);
}

// response data handling middleware
app.useAsync(responseHandler);

// Update Cache with response
if (applicationCacheEnabled) {
  app.useAsync(responseCacheUpdateHandler);
}

// response error handling middleware
app.useAsync(handleError);

process.on('unhandledRejection', handleRejection);
process.on('uncaughtException', handleUncaughtException);

if (!isTestEnv()) {
  const server = app.listen(port, () => {
    logger.info(`Server running on port: ${port}`);
  });

  // Health check endpoints
  createTerminus(server, {
    healthChecks: {
      '/health/readiness': health.readinessCheck,
      '/health/liveness': health.livenessCheck,
    },
    onShutdown: health.onShutdown,
  });
}

export default app;
