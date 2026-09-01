// SPDX-License-Identifier: Apache-2.0

import {getOpenApiMap} from './openapiHandler.js';
import {filterKeys} from '../constants.js';
import isEmpty from 'lodash/isEmpty';

const openApiMap = getOpenApiMap();

// Multiple values of these params can be collapsed to the last value
const COLLAPSABLE_PARAMS = [filterKeys.BALANCE, filterKeys.BLOCK_HASH, filterKeys.NONCE, filterKeys.SCHEDULED];

/**
 * Do not sort these query parameters as the results of the sql query changes based on their order
 *
 * Some examples from the spec tests:
 *   /api/v1/contracts/results?block.number=11&block.number=10 sorts the results differently than ?block.number=10&block.number=11
 *
 * From historical-custom-fees.json:
 *   /api/v1/tokens/1135?timestamp=lt:1234567899.999999000&timestamp=1234567899.999999001 returns a result whereas
 *   ?timestamp=1234567899.999999001&timestamp=lt:1234567899.999999000 returns a 404
 */
const NON_SORTED_PARAMS = COLLAPSABLE_PARAMS.concat([filterKeys.BLOCK_NUMBER, filterKeys.TIMESTAMP]);

/**
 * Normalizes a request by adding any missing default values and sorting any array query parameters.
 *
 * It is expected that this is called after any error handling for the request
 *
 * @param openApiRoute {string}
 * @param path {string}
 * @param query request query object
 * @returns {string}
 */
const normalizeRequestQueryParams = (openApiRoute, path, query) => {
  const openApiParameters = openApiMap.get(openApiRoute);
  if (isEmpty(openApiParameters)) {
    return toNormalizedPath(path, query);
  }

  const normalizedQuery = {};
  for (const param of openApiParameters) {
    const name = param.parameterName;
    const value = query[name];
    let normalizedValue;
    if (value !== undefined) {
      normalizedValue = Array.isArray(value) ? getNormalizedArrayValue(name, value) : value;
    } else if (param?.defaultValue !== undefined) {
      // Add the default value to the query parameter
      normalizedValue = param.defaultValue;
    }

    if (!isEmpty(normalizedValue)) {
      normalizedQuery[name] = normalizedValue;
    }
  }

  return toNormalizedPath(path, normalizedQuery);
};

const stringifyQuery = (query) => {
  return Object.entries(query)
    .flatMap(([name, value]) => {
      const encodedName = encodeURIComponent(name);
      const values = Array.isArray(value) ? value : [value];
      return values.map((v) => `${encodedName}=${encodeURIComponent(v)}`);
    })
    .join('&');
};

const toNormalizedPath = (path, query) => {
  return isEmpty(query) ? path : path + '?' + stringifyQuery(query);
};

const getNormalizedArrayValue = (name, valueArray) => {
  if (isEmpty(valueArray)) {
    return;
  }

  if (!NON_SORTED_PARAMS.includes(name)) {
    // Sort the order of the parameters within the array
    valueArray.sort();
  } else if (COLLAPSABLE_PARAMS.includes(name)) {
    // Only add the last item in the array to the query parameter
    valueArray = valueArray.slice(valueArray.length - 1);
  }

  return valueArray;
};

export {normalizeRequestQueryParams};
