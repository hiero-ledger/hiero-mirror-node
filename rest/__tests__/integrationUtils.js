// SPDX-License-Identifier: Apache-2.0

import {jest} from '@jest/globals';
import {exec} from 'child_process';

import integrationContainerOps from './integrationContainerOps';
import testExports from '../timestampRange';
import {TokenService} from '../service';
import {getMirrorConfig} from '../config.js';
import EntityId from '../entityId.js';

// set a large timeout for beforeAll as downloading docker image if not exists can take quite some time. Note
// it's 12 minutes for CI to workaround possible DockerHub rate limit.
const defaultBeforeAllTimeoutMillis = process.env.CI ? 12 * 60 * 1000 : 4 * 60 * 1000;

const {
  common: {realm: systemRealm, shard: systemShard},
} = getMirrorConfig();

const transformShardRealmValues = (obj) => {
  const shardRealm = `${systemShard}.${systemRealm}.`;

  if (Array.isArray(obj)) {
    return obj.map(transformShardRealmValues);
  } else if (typeof obj === 'object' && obj !== null) {
    return Object.fromEntries(
      Object.entries(obj).map(([key, value]) => {
        if (
          (typeof value === 'string' && key.toLowerCase().endsWith('end')) ||
          key.toLowerCase().endsWith('time') ||
          key.toLowerCase() === 'hapi_version' ||
          key.toLowerCase() === 'from' ||
          key.toLowerCase().includes('start') ||
          key.toLowerCase().includes('timestamp') ||
          key.toLowerCase() === 'to'
        ) {
          return [key, value]; // leave unchanged
        }
        return [key, transformShardRealmValues(value)];
      })
    );
  } else if (typeof obj === 'string') {
    if (/^0\.0\.\d+$/.test(obj)) {
      return obj.replace(/^0\.0\./, `${shardRealm}`);
    }

    if (/^0\.0\.[A-Z0-9]{40,}$/i.test(obj)) {
      return obj.replace(/^0\.0\./, shardRealm);
    }

    if (/^0\.[A-Z0-9]{40,}$/i.test(obj)) {
      return obj.replace(/^0\./, `${systemRealm}.`);
    }

    if (/^0\.\d+$/.test(obj)) {
      return obj.replace(/^0\./, `${systemRealm}.`);
    }

    let possibleUrlParts = obj.split(/[?&]/);
    let result = [];
    for (let i = 0; i < possibleUrlParts.length; i++) {
      let entry = possibleUrlParts[i];
      if (/timestamp=(?:gt|gte|lt|lte|eq)?:?\d+\.\d+/i.test(entry)) {
        result.push(entry);
        continue;
      }

      entry = entry.replace(/0\.0\.(\d+)/g, `${shardRealm}$1`);
      entry = entry.replace(/0\.0\.([A-Z0-9]{40,})/gi, `${shardRealm}$1`);
      entry = entry.replace(/0\.([A-Z0-9]{40,})/gi, `${systemRealm}.$1`);
      entry = entry.replace(/(?<!\d)0\.(\d+)\b/g, `${systemRealm}.$1`);

      result.push(entry);
    }

    return result.join('&').replace('&', '?');
  } else {
    return obj;
  }
};

const encodedIdFromSpecValue = (value) => {
  if (value === null || value === undefined) {
    return value;
  }
  const transformedValue = transformShardRealmValues(value);
  return EntityId.parseString(`${transformedValue}`).getEncodedId();
};

const applyResponseJsonMatrix = (spec, key) => {
  if (spec.responseJsonMatrix?.[key]) {
    spec.responseJson = transformShardRealmValues({
      ...spec.responseJson,
      ...spec.responseJsonMatrix[key],
    });
  }

  if (spec.tests) {
    for (const test of spec.tests) {
      if (test.responseJsonMatrix?.[key]) {
        test.responseJson = {
          ...test.responseJson,
          ...test.responseJsonMatrix[key],
        };
      }
    }
  }

  return spec;
};

const isDockerInstalled = function () {
  return new Promise((resolve) => {
    exec('docker --version', (err) => {
      resolve(!err);
    });
  });
};

const setupIntegrationTest = () => {
  jest.retryTimes(3);
  jest.setTimeout(40000);

  beforeAll(async () => {
    return await integrationContainerOps.initializeContainers();
  }, defaultBeforeAllTimeoutMillis);

  afterAll(async () => {
    await ownerPool?.end();
    await pool?.end();
  });

  beforeEach(async () => {
    await integrationContainerOps.cleanUp();
    TokenService.clearTokenCache();
    testExports.getFirstTransactionTimestamp.reset();
  });
};

export {
  applyResponseJsonMatrix,
  defaultBeforeAllTimeoutMillis,
  encodedIdFromSpecValue,
  isDockerInstalled,
  setupIntegrationTest,
  transformShardRealmValues,
};
