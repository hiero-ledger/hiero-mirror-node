// SPDX-License-Identifier: Apache-2.0

const urlPrefix = '/api/v1';

export const setEnvDefault = (name, defaultValue) => (__ENV[name] = __ENV[name] || defaultValue);

// set up common default values
setEnvDefault('BASE_URL', 'http://localhost');
setEnvDefault('DEFAULT_DURATION', '120s');
setEnvDefault('DEFAULT_GRACEFUL_STOP', '5s');
setEnvDefault('DEFAULT_LIMIT', 100);
setEnvDefault('DEFAULT_SETUP_TIMEOUT', '5m');
setEnvDefault('DEFAULT_VUS', 10);
__ENV['BASE_URL_PREFIX'] = __ENV['BASE_URL'] + urlPrefix;

const copyEnvParamsFromEnvMap = (propertyList) => {
  const envProperties = {};
  let allPropertiesFound = true;
  for (const property of propertyList) {
    if (__ENV.hasOwnProperty(property)) {
      envProperties[property] = __ENV[property];
    } else {
      allPropertiesFound = false;
    }
  }
  return {
    allPropertiesFound,
    envProperties,
  };
};

export const computeProperties = (propertyList, fallback) => {
  console.info(`Computing parameters ${propertyList}`);
  const copy = copyEnvParamsFromEnvMap(propertyList);
  if (copy.allPropertiesFound) {
    console.info(`All parameters found in environment: ${propertyList}`);
    return copy.envProperties;
  }

  try {
    console.info(`Computing parameters ${propertyList} using fallback`);
    Object.assign(copy.envProperties, fallback());
  } catch (err) {
    console.warn(`${err.message}`);
  }

  return copy.envProperties;
};

export const computeTestParameters = (requiredParameters, allHandlers) => {
  if (requiredParameters.length === 0) {
    // when the test case doesn't have required parameters
    return {};
  }

  // run handlers which can provide any of the required parameters
  return allHandlers
    .filter((handler) => requiredParameters.some((parameter) => handler.supportedParameters.includes(parameter)))
    .reduce((previous, handler) => Object.assign(previous, handler()), {});
};

export const getValidResponse = (requestUrl, requestBody, httpVerbMethod) => {
  const response = httpVerbMethod(requestUrl, JSON.stringify(requestBody));
  if (response.status !== 200) {
    throw new Error(`${response.status} received when requesting ${requestUrl}`);
  }
  return JSON.parse(response.body);
};

export const wrapComputeParametersFunc = (supportedParameters, fallback) => {
  const func = () => computeProperties(supportedParameters, fallback);
  func.supportedParameters = supportedParameters;
  return func;
};
