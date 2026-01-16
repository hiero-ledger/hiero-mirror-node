// SPDX-License-Identifier: Apache-2.0

import compression from 'compression';
import cors from 'cors';
import express from 'express';

import config from './config';
import common from './common';
import logger from './logger';
import {runEverything} from './monitor';

const app = express();

const port = process.env.PORT || config.port || 3000;
if (Number.isNaN(Number.parseInt(port))) {
  logger.error('Please specify a valid port');
  process.exit(1);
}

app.disable('x-powered-by');
app.set('trust proxy', true);
app.set('port', port);
app.use(
  express.urlencoded({
    extended: false,
  })
);
app.use(express.json());
app.use(compression());
app.use(cors());

const apiPrefix = '/api/v1';
const healthDown = '{"status": "DOWN"}';
const healthUp = '{"status": "UP"}';

common.initResults();

// routes
app.get('/health/liveness', (req, res) => res.status(200).send(healthUp));
app.get('/health/readiness', (req, res) => {
  const status = common.getStatus();
  const total = status.results.map((r) => (r.results.testResults ? 1 : 0)).reduce((r, i) => r + i);

  if (total > 0) {
    res.status(200).send(healthUp);
  } else {
    res.status(503).send(healthDown);
  }
});

const allTestResultTypes = Object.values(common.TEST_RESULT_TYPES);

const parseResultQueryParam = (req) => {
  const value = (req.query.result ?? common.TEST_RESULT_TYPES.FAILED).toLowerCase();
  return allTestResultTypes.includes(value) ? value : common.TEST_RESULT_TYPES.FAILED;
};

app.get(`${apiPrefix}/status`, (req, res) => {
  const status = common.getStatus(parseResultQueryParam(req));
  const passed = status.results.map((r) => r.results.numPassedTests).reduce((r, i) => r + i, 0) || 0;
  const total =
    status.results.map(({results}) => results.numFailedTests + results.numPassedTests).reduce((r, i) => r + i, 0) || 0;
  logger.info(
    `${req.ip} ${req.method} ${req.originalUrl} returned ${status.httpCode}: ${passed}/${total} tests passed`
  );
  res.status(status.httpCode).json(status.results);
});

app.get(`${apiPrefix}/status/:name`, (req, res) => {
  const status = common.getStatusByName(req.params.name, parseResultQueryParam(req));
  const {results} = status;
  const passed = results.numPassedTests;
  const total = results.numFailedTests + results.numPassedTests;
  logger.info(
    `${req.ip} ${req.method} ${req.originalUrl} returned ${status.httpCode}: ${passed}/${total} tests passed`
  );
  res.status(status.httpCode).json(status);
});

if (process.env.NODE_ENV !== 'test') {
  app.listen(port, () => {
    logger.info(`Server running on port: ${port}`);
  });
}

const servers = [...config.servers].map((server) => {
  return {
    ...server,
    running: false,
  };
});

// Run tests on startup and every interval after that
runEverything(servers)
  .catch((e) => logger.error(`Error starting server: ${JSON.stringify(e)}`))
  .then(() => {
    setInterval(async () => {
      await runEverything(servers);
    }, config.interval * 1000);
  });

export default app;
