// SPDX-License-Identifier: Apache-2.0

import {execSync} from 'child_process';
import fs from 'fs';
import path from 'path';

const FLYWAY_DATA_PATH = path.join('.', 'build', 'flyway');
const FLYWAY_EXE_PATH = path.join('.', 'node_modules', 'node-flywaydb', 'bin', 'flyway');
const FLYWAY_VERSION = '11.8.2';

const initializeFlyway = () => {
  const flywayConfigPath = path.join(FLYWAY_DATA_PATH, `config_global.json`);
  const flywayConfig = {
    flywayArgs: {
      url: 'jdbc:postgresql://127.0.0.1:-1/invalid',
    },
    version: FLYWAY_VERSION,
    downloads: {
      storageDirectory: FLYWAY_DATA_PATH,
    },
  };

  fs.mkdirSync(FLYWAY_DATA_PATH, {recursive: true});
  fs.writeFileSync(flywayConfigPath, JSON.stringify(flywayConfig));

  let retries = 10;
  while (retries-- > 0) {
    try {
      execSync(`node ${FLYWAY_EXE_PATH} -c ${flywayConfigPath} info`, {stdio: 'pipe'});
      break;
    } catch (e) {
      const errMessage = e.stderr.toString();
      if (errMessage.includes('-1 not valid')) {
        console.log(e.stdout.toString());
        break;
      } else {
        console.log(errMessage);
      }
    }
  }

  if (retries < 0) {
    throw new Error('Failed to initialize flyway');
  }
};

export default async function (globalConfig, projectConfig) {
  initializeFlyway();
}

export {FLYWAY_DATA_PATH, FLYWAY_EXE_PATH, FLYWAY_VERSION};
