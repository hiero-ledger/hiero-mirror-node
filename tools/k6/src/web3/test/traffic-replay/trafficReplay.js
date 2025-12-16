// SPDX-License-Identifier: Apache-2.0

import {getOptionsWithScenario} from '../../../lib/common.js';
import check from 'k6';
import http from 'k6/http';

const BASE_URL = __ENV.BASE_URL;

// Read a full line containing only the JSON request body uncompressed.
const DATA_REGEX = /(^\{.*\}$)/gm;

const params = {
  headers: {
    'Content-Type': 'application/json',
    'Accept-Encoding': 'gzip',
  },
};

const options = getOptionsWithScenario('trafficReplay', null, {});

function parseRequests(fileContent) {
  const requests = [];
  let requestIndex = 0;
  let match;

  while ((match = DATA_REGEX.exec(fileContent)) !== null) {
    const rawJsonBody = match[1];
    try {
      requests.push(rawJsonBody);
      requestIndex++;
    } catch (e) {
      console.error(`Failed to parse JSON at index ${requestIndex}: ${e.message}`);
    }
  }
  return requests;
}

function run(testParameters) {
  if (!testParameters) {
    // This test case must be run via apis.js in order to download the traffic input file from github only once
    // instead of once for each VU. With a large amount of VUs, github rejects some of the request which
    // causes the tests to fail and contaminates the results.
    console.log('Skipping test execution as no parsed requests found.');
    return;
  }
  const parsedRequests = testParameters.trafficReplayRequests || [];
  if (parsedRequests.length === 0) {
    console.log('No traffic replay requests found.');
    return;
  }

  const totalRequests = parsedRequests.length;
  const requestIndex = __ITER % totalRequests;
  const requestData = parsedRequests[requestIndex];

  const url = `${BASE_URL}/api/v1/contracts/call`;
  const res = http.post(url, requestData, params);
  check(res, {
    'Traffic replay OK': (r) => r.status === 200 || r.status === 400, // Some of the requests are expected to revert.
  });
}

export {options, run, parseRequests};
