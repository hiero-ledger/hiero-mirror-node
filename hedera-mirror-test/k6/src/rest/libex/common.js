// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {setupTestParameters} from './parameters.js';
import {TestScenarioBuilder} from '../../lib/common.js';

const isSuccess = (response) => response.status >= 200 && response.status < 300;

const isValidListResponse = (response, listName) => {
  if (!isSuccess(response)) {
    return false;
  }

  const body = JSON.parse(response.body);
  const list = body[listName];
  if (!Array.isArray(list)) {
    return false;
  }

  return list.length > 0;
};

class RestTestScenarioBuilder extends TestScenarioBuilder {
  constructor() {
    super();
    this.fallbackRequest((testParameters) => {
      const url = `${testParameters['BASE_URL_PREFIX']}/transactions`;
      return http.get(url);
    });
  }

  build() {
    return Object.assign(super.build(), {setup: () => setupTestParameters(this._requiredParameters)});
  }
}

export {isValidListResponse, isSuccess, RestTestScenarioBuilder};
