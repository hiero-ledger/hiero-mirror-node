// SPDX-License-Identifier: Apache-2.0

import common from './common';
import {runTests} from './monitor_tests';

let runCount = 0;

/**
 * Main function to run the tests and save results
 * @param {Array} servers array of servers to test
 * @return None
 */
const runEverything = async (servers) => {
  const serverTests = [];
  const startTime = Date.now();
  const currentRun = ++runCount;
  logger.info(`Running test #${currentRun}`);

  servers.forEach((server) => {
    server.run = currentRun;
    if (server.running) {
      logger.warn(`Skipping test run #${currentRun} for ${server.name} since a previous run is still in progress`);
      return;
    }

    server.running = true;

    const serverTest = runTests(server)
      .then((results) => {
        const total = results.testResults.length;
        const elapsed = Date.now() - startTime;
        logger.info(
          `Completed test run #${currentRun} for ${server.name} with ${results.numPassedTests}/${total} tests passed in ${elapsed} ms`
        );
        common.saveResults(server, results);
      })
      .catch((error) => {
        const elapsed = Date.now() - startTime;
        logger.error(`Error running tests #${currentRun} for ${server.name} in ${elapsed} ms: ${error.stack}`);
      })
      .finally(() => {
        server.running = false;
      });

    serverTests.push(serverTest);
  });

  return Promise.all(serverTests).then(() => {
    const elapsed = Date.now() - startTime;
    logger.info(`Finished test run #${currentRun} in ${elapsed} ms`);
  });
};

export {runEverything};
