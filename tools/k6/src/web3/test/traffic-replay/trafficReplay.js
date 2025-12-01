// SPDX-License-Identifier: Apache-2.0

const FILE_PATH = './input.txt';
const BASE_URL = __ENV.BASE_URL;
const VUS_COUNT = __ENV.DEFAULT_VUS ? parseInt(__ENV.DEFAULT_VUS) : 1;

// Regex to capture the nanosecond timestamp and the request body
const DATA_REGEX = /(\d+)\s+({.*})$/gm;

const parsedRequests = new SharedArray('parsed payloads', function () {
  const fileContent = open(FILE_PATH);
  const requests = [];

  // We use a mutable index to ensure the regex finds the next match correctly
  let match;
  while ((match = DATA_REGEX.exec(fileContent)) !== null) {
    // match[1] is the nanosecond timestamp
    const scenarioKey = match[1];
    // match[2] is the full JSON string
    const rawJsonBody = match[2];

    try {
      requests.push({
        scenarioKey: scenarioKey,
        payload: rawJsonBody,
      });
    } catch (e) {
      console.error(`Failed to parse JSON for timestamp ${scenarioKey}: ${e.message}`);
    }
  }
  return requests;
});

const params = {
  headers: {
    'Content-Type': 'application/json',
    'Accept-Encoding': 'gzip',
  },
};

const {options, run} = new TrafficReplayScenarioBuilder(parsedRequests)
  .name('trafficReplay')
  .url(`${__ENV.BASE_URL}/api/v1/contracts/call`)
  .request((url, payload) => http.post(url, payload, params))
  .check('Traffic replay OK', (r) => r.status === 200)
  .build();

export {options, run};
