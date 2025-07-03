// SPDX-License-Identifier: Apache-2.0

// ext libraries
import extend from 'extend';

import client from 'prom-client';
import swStats from 'swagger-stats';
import url from 'url';

// files
import config from '../config';

import {getV1OpenApiObject} from './openapiHandler';
import {ipMask} from '../utils';
import _ from 'lodash';

const onMetricsAuthenticate = async (req, username, password) => {
  return new Promise(function (resolve, reject) {
    const match = username === config.metrics.config.username && password === config.metrics.config.password;
    resolve(match);
  }).catch((err) => {
    logger.debug(`Auth error: ${err}`);
    throw err;
  });
};

const ipEndpointHistogram = new client.Counter({
  name: 'hiero_mirror_rest_request_count',
  help: 'a counter mapping ip addresses to the endpoints they hit',
  labelNames: ['endpoint', 'ip'],
});

const recordIpAndEndpoint = (req, res, next) => {
  if (req.route !== undefined) {
    ipEndpointHistogram.labels(req.route.path, ipMask(req.ip)).inc();
  }
};

const metricsHandler = () => {
  // We removed stateproof from OpenAPI, but we still want to capture metrics from it
  const openApiSpec = _.cloneDeep(getV1OpenApiObject());
  openApiSpec.paths['/api/v1/transactions/{transactionId}/stateproof'] = {get: {}};

  const defaultMetricsConfig = {
    name: process.env.npm_package_name,
    onAuthenticate: onMetricsAuthenticate,
    swaggerSpec: openApiSpec,
    version: process.env.npm_package_version,
  };

  // combine defaultMetricsConfig with file defined configs
  extend(true, defaultMetricsConfig, config.metrics.config);

  const swaggerPath = `${config.metrics.config.uriPath}`;
  const metricsPath = `${swaggerPath}/metrics/`;
  const swaggerStats = swStats.getMiddleware(defaultMetricsConfig);

  return function filter(req, res, next) {
    let {pathname} = url.parse(req.url, false);
    pathname += pathname.endsWith('/') ? '' : '/';

    // Ignore all the other swagger stat endpoints
    if (pathname.startsWith(swaggerPath) && pathname !== metricsPath) {
      return next();
    }

    return swaggerStats(req, res, next);
  };
};

export {metricsHandler, recordIpAndEndpoint};
