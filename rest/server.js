// SPDX-License-Identifier: Apache-2.0

// external libraries
import assert from 'assert';
import express from 'express';

import {createTerminus} from '@godaddy/terminus';
import cors from 'cors';
import httpContext from 'express-http-context';
import compression from 'compression';

// local files
import accounts from './accounts';
import balances from './balances';
import extendExpress from './extendExpress';
import config from './config';
import * as constants from './constants';
import health from './health';
import schedules from './schedules';
import stateproof from './stateproof';
import tokens from './tokens';
import topicmessage from './topicmessage';
import transactions from './transactions';
import {isTestEnv} from './utils';

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
} from './middleware';

// routes
import {AccountRoutes, BlockRoutes, ContractRoutes, NetworkRoutes} from './routes';
import {handleRejection, handleUncaughtException} from './middleware/httpErrorHandler';
import {initializePool} from './dbpool';

// use a dummy port for jest unit tests
const port = isTestEnv() ? 3000 : config.port;
if (port === undefined || Number.isNaN(Number(port))) {
  logger.error('Server started with unknown port');
  process.exit(1);
}

// Postgres pool
initializePool();

// Express configuration. Prior to v0.5 all sets should be configured before use or they won't be picked up
const app = extendExpress(express());
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
app.useExt(requestLogger);

// metrics middleware
if (config.metrics.enabled) {
  app.useExt(metricsHandler());
}

// Check for cached response
if (applicationCacheEnabled) {
  logger.info('Response caching is enabled');
  app.useExt(responseCacheCheckHandler);
}

// accounts routes
app.getExt(`${apiPrefix}/accounts`, accounts.getAccounts);
app.getExt(`${apiPrefix}/accounts/:${constants.filterKeys.ID_OR_ALIAS_OR_EVM_ADDRESS}`, accounts.getOneAccount);
app.use(`${apiPrefix}/${AccountRoutes.resource}`, AccountRoutes.router);

// balances routes
app.getExt(`${apiPrefix}/balances`, balances.getBalances);

// contracts routes
app.use(`${apiPrefix}/${ContractRoutes.resource}`, ContractRoutes.router);

// network routes
app.use(`${apiPrefix}/${NetworkRoutes.resource}`, NetworkRoutes.router);

// block routes
app.use(`${apiPrefix}/${BlockRoutes.resource}`, BlockRoutes.router);

// schedules routes
app.getExt(`${apiPrefix}/schedules`, schedules.getSchedules);
app.getExt(`${apiPrefix}/schedules/:scheduleId`, schedules.getScheduleById);

// stateproof route
if (config.stateproof.enabled || isTestEnv()) {
  logger.info('stateproof REST API is enabled, install handler');
  app.getExt(`${apiPrefix}/transactions/:transactionId/stateproof`, stateproof.getStateProofForTransaction);
} else {
  logger.info('stateproof REST API is disabled');
}

// tokens routes
app.getExt(`${apiPrefix}/tokens`, tokens.getTokensRequest);
app.getExt(`${apiPrefix}/tokens/:tokenId`, tokens.getTokenInfoRequest);
app.getExt(`${apiPrefix}/tokens/:tokenId/balances`, tokens.getTokenBalances);
app.getExt(`${apiPrefix}/tokens/:tokenId/nfts`, tokens.getNftTokensRequest);
app.getExt(`${apiPrefix}/tokens/:tokenId/nfts/:serialNumber`, tokens.getNftTokenInfoRequest);
app.getExt(`${apiPrefix}/tokens/:tokenId/nfts/:serialNumber/transactions`, tokens.getNftTransferHistoryRequest);

// topics routes
app.getExt(`${apiPrefix}/topics/:topicId/messages`, topicmessage.getTopicMessages);
app.getExt(`${apiPrefix}/topics/:topicId/messages/:sequenceNumber`, topicmessage.getMessageByTopicAndSequenceRequest);
app.getExt(`${apiPrefix}/topics/messages/:consensusTimestamp`, topicmessage.getMessageByConsensusTimestamp);

// transactions routes
app.getExt(`${apiPrefix}/transactions`, transactions.getTransactions);
app.getExt(`${apiPrefix}/transactions/:transactionIdOrHash`, transactions.getTransactionsByIdOrHash);

// record ip metrics if enabled
if (config.metrics.ipMetrics) {
  app.useExt(recordIpAndEndpoint);
}

// response data handling middleware
app.useExt(responseHandler);

// Update Cache with response
if (applicationCacheEnabled) {
  app.useExt(responseCacheUpdateHandler);
}

// response error handling middleware
app.useExt(handleError);

process.on('unhandledRejection', handleRejection);
process.on('uncaughtException', handleUncaughtException);

if (!isTestEnv()) {
  const server = app.listen(port, '0.0.0.0', (err) => {
    if (err) {
      throw err;
    }

    logger.info(`Server running on port: ${port}`);
  });

  // Health check endpoints
  createTerminus(server, {
    healthChecks: {
      '/health/readiness': health.readinessCheck,
      '/health/liveness': health.livenessCheck,
    },
    logger: (msg, err) => logger.error(msg, err),
    onShutdown: health.onShutdown,
  });
}

export default app;
