// SPDX-License-Identifier: Apache-2.0

const FILE_PATH = './input.txt';
const BASE_URL = __ENV.BASE_URL;

// Read a full line containing only the JSON request body uncompressed.
const DATA_REGEX = /(^\{.*\}$)/gm;

const parsedRequests = new SharedArray('parsed payloads', function () {
  const fileContent = open(FILE_PATH);
  const requests = [];

  let requestIndex = 0;
  let match;
  while ((match = DATA_REGEX.exec(fileContent)) !== null) {
    const rawJsonBody = match[1];

    try {
      requests.push({
        scenarioKey: String(requestIndex),
        payload: rawJsonBody,
      });
      requestIndex++;
    } catch (e) {
      console.error(`Failed to parse JSON for index ${requestIndex}: ${e.message}`);
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

const options = utils.getOptionsWithScenario('trafficReplay', null, {});

function run() {
  const totalRequests = parsedRequests.length;
  const requestIndex = __ITER % totalRequests;
  const requestData = parsedRequests[requestIndex];

  const rawPayload = requestData.payload;
  const scenarioKey = requestData.scenarioKey;

  const url = `${BASE_URL}/api/v1/contracts/call`;
  const dynamicParams = Object.assign({}, params, {
    tags: {
      name: `traffic_replay_index_${requestIndex}`,
      scenario_key: scenarioKey,
    },
  });

  const res = http.post(url, rawPayload, dynamicParams);
  check(res, {
    [`Traffic replay index ${requestIndex} processed in ${res.timings.duration}ms`]: (r) =>
      r.status === 200 || r.status === 400, // Some of the requests are expected to revert.
  });
}

export {options, run};
