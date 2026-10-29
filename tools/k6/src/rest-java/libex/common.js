// SPDX-License-Identifier: Apache-2.0

import http from 'k6/http';

import {setupTestParameters} from './parameters.js';
import {TestScenarioBuilder} from '../../lib/common.js';

const isSuccess = (response) => response.status >= 200 && response.status < 300;

const fromHex = (hex) => {
  const bytes = new Uint8Array(hex.length / 2);
  for (let i = 0; i < bytes.length; i++) {
    bytes[i] = parseInt(hex.slice(i * 2, i * 2 + 2), 16);
  }
  return bytes.buffer;
};

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
    super('RESTJAVA');
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

export {fromHex, isValidListResponse, isSuccess, RestJavaTestScenarioBuilder};
