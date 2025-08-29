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

class RestJavaTestScenarioBuilder extends TestScenarioBuilder {
  constructor() {
    super();
    this.fallbackRequest((testParameters) => {
      // Fallback to a known account ID. Replace this with another API endpoint when one exists not requiring an entity ID.
      const url = `${testParameters['BASE_URL_PREFIX']}/accounts/2/allowances/nfts`;
      return http.get(url);
    });
  }

  build() {
    return Object.assign(super.build(), {setup: () => setupTestParameters(this._requiredParameters)});
  }
}

export {isValidListResponse, isSuccess, RestJavaTestScenarioBuilder};
