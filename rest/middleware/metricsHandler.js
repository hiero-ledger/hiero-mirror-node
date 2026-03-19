// SPDX-License-Identifier: Apache-2.0

import extend from 'extend';
import cloneDeep from 'lodash/cloneDeep';
import swStats from 'swagger-stats';
import url from 'url';

import config from '../config';
import {getV1OpenApiObject} from './openapiHandler';

const onMetricsAuthenticate = async (req, username, password) => {
  return new Promise(function (resolve, reject) {
    const match = username === config.metrics.config.username && password === config.metrics.config.password;
    resolve(match);
  }).catch((err) => {
    logger.debug(`Auth error: ${err}`);
    throw err;
  });
};

const metricsHandler = () => {
  const openApiSpec = cloneDeep(getV1OpenApiObject());

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

export {metricsHandler};
