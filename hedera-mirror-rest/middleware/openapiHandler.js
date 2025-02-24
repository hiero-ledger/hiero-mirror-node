// SPDX-License-Identifier: Apache-2.0

import OpenApiValidator from 'express-openapi-validator';
import fs from 'fs';
import yaml from 'js-yaml';
import _ from 'lodash';
import path from 'path';
import swaggerUi from 'swagger-ui-express';

// files
import config from '../config';
import {isTestEnv} from '../utils.js';

let v1OpenApiDocument;
let v1OpenApiFile;
let openApiMap;

const OPEN_API_PARAMETER_LOCATION = '#/components/parameters/';

/**
 * Check if apiVersion is currently supported
 * @param {Number} apiVersion
 */
const isInValidVersionRange = (apiVersion) => {
  if (!_.isNumber(apiVersion)) {
    return false;
  }

  // current api version range
  return apiVersion > 0 && apiVersion < 2;
};

/**
 * Get path of open api spec
 * @param {Number} apiVersion
 */
const getSpecPath = (apiVersion) => {
  const apiVersionPath = isInValidVersionRange(apiVersion) ? `v${apiVersion}` : 'v1';
  return `api/${apiVersionPath}/${config.openapi.specFileName}.yml`;
};

/**
 * Get YAML object representing the open api spec
 * @param {Number} apiVersion
 */
const getOpenApiSpecObject = (apiVersion) => {
  return yaml.load(getV1OpenApiFile(apiVersion));
};

/**
 * Get the YAML file of the open api spec for the v1 rest api
 */
const getV1OpenApiFile = (apiVersion) => {
  if (_.isUndefined(v1OpenApiFile)) {
    const openApiSpecPath = path.resolve(process.cwd(), getSpecPath(apiVersion));
    v1OpenApiFile = fs.readFileSync(openApiSpecPath, 'utf8');
  }

  return v1OpenApiFile;
};

/**
 * Get the YAML object of the open api spec for the v1 rest api
 */
const getV1OpenApiObject = () => {
  if (_.isUndefined(v1OpenApiDocument)) {
    v1OpenApiDocument = getOpenApiSpecObject(1);
  }

  return v1OpenApiDocument;
};

/**
 * Get the path to parameter properties map for the OpenApi Spec
 *
 * @returns {Map<string, Array<{parameterName, defaultValue}>>}
 */
const getOpenApiMap = () => {
  if (_.isUndefined(openApiMap)) {
    const openApiObject = getV1OpenApiObject();
    const map = new Map();
    Object.keys(openApiObject.paths).forEach((path) => {
      const parameters = getOpenApiParameters(path, openApiObject);
      map.set(path, parameters);
    });
    openApiMap = map;
  }

  return openApiMap;
};

/**
 * Given a path, gets the query parameters and their default values
 * @param path {string}
 * @param openApiObject
 * @returns {Array<{parameterName, defaultValue}>}
 */
const getOpenApiParameters = (path, openApiObject) => {
  const pathObject = openApiObject.paths[path];
  const parameters = pathObject?.get?.parameters;
  if (parameters === undefined) {
    return {};
  }

  return (
    parameters
      // Each open api parameter is prefixed by #/components/parameters/
      .filter((p) => p.$ref?.includes(OPEN_API_PARAMETER_LOCATION))
      .map((p) => p.$ref.substring(OPEN_API_PARAMETER_LOCATION.length))
      .map((p) => openApiObject.components.parameters[p])
      .filter((p) => p.in !== 'path')
      .map((p) => {
        const parameterName = p.name;
        let defaultValue = p.schema?.default;
        if (defaultValue !== undefined && !_.isString(defaultValue)) {
          // Convert all values to strings
          defaultValue = '' + defaultValue;
        }

        return {parameterName, defaultValue};
      })
  );
};

const serveSpec = (req, res) => res.type('text/yaml').send(getV1OpenApiFile());

/**
 * Serve the open api spec on the given express object
 * @param {ExpressWithAsync} app
 */
const serveSwaggerDocs = (app) => {
  const options = {
    explorer: false,
    customCss:
      '.topbar-wrapper img { content:url(https://camo.githubusercontent.com/cca6b767847bb8ca5c7059481ba13a5fc81c5938/68747470733a2f2f7777772e6865646572612e636f6d2f6c6f676f2d6361706974616c2d686261722d776f72646d61726b2e6a7067); }',
    swaggerOptions: {
      operationsSorter: 'alpha',
      tagsSorter: 'alpha',
    },
  };

  app.get(`/api/v1/${config.openapi.swaggerUIPath}/${config.openapi.specFileName}.yml`, serveSpec);
  app.get(`/api/v1/${config.openapi.swaggerUIPath}/${config.openapi.specFileName}.yaml`, serveSpec);
  app.use(`/api/v1/${config.openapi.swaggerUIPath}`, swaggerUi.serve, swaggerUi.setup(getV1OpenApiObject(), options));
};

const openApiValidator = (app) => {
  const validateResponses = isTestEnv() ? {allErrors: true} : false;
  app.use(
    OpenApiValidator.middleware({
      apiSpec: path.resolve(process.cwd(), getSpecPath(1)),
      ignoreUndocumented: true,
      validateRequests: false,
      validateResponses,
    })
  );
};

export {getV1OpenApiObject, getOpenApiMap, openApiValidator, serveSwaggerDocs};
