// SPDX-License-Identifier: Apache-2.0

/**
 * Integration tests for the rest-api and postgresql database.
 * Tests will be performed using either a docker postgres instance managed by the testContainers module or
 * some other database (running locally, instantiated in the CI environment, etc).
 * Tests instantiate the database schema via a flywaydb wrapper using the flyway CLI to clean and migrate the
 * schema (using sql files in the ../src/resources/db/migration directory).
 *
 * * Test data for rest-api tests is created by:
 * 1) reading account id, balance, expiration and crypto transfer information from specs/*.json
 * 2) applying account creations, balance sets and transfers to the integration DB

 * Tests are then run in code below and by comparing requests/responses from the server to data in the specs/ dir.
 */

// external libraries
import crypto from 'crypto';
import fs from 'fs';
import {jest} from '@jest/globals';
import _ from 'lodash';
import path from 'path';
import request from 'supertest';
import integrationDomainOps from '../integrationDomainOps';
import IntegrationS3Ops from '../integrationS3Ops';
import config, {getMirrorConfig} from '../../config';
import {cloudProviders} from '../../constants';
import server from '../../server';
import {getModuleDirname} from '../testutils';
import {JSONParse} from '../../utils';
import {defaultBeforeAllTimeoutMillis, setupIntegrationTest} from '../integrationUtils';
import {CreateBucketCommand, PutObjectCommand, S3} from '@aws-sdk/client-s3';
import sinon from 'sinon';
import integrationContainerOps from '../integrationContainerOps';

const groupSpecPath = $$GROUP_SPEC_PATH$$;

const defaultResponseHeaders = {
  'cache-control': 'public, max-age=1',
};
const responseHeadersFilename = 'responseHeaders.json';

const {
  common: {realm: systemRealm, shard: systemShard},
} = getMirrorConfig();

let specRootPath;

const walk = (dir, files = []) => {
  for (const f of fs.readdirSync(dir)) {
    const p = path.join(dir, f);
    const stat = fs.statSync(p);

    if (stat.isDirectory()) {
      walk(p, files);
    } else {
      files.push(p);
    }
  }

  return files;
};

/**
 * Recursively search for responseHeaders.json file from the directory of the spec file, up to the spec root directory.
 * Return the content of the first such file or the default response headers if none found.
 *
 * @param specPath
 * @returns {*} response headers
 */
const getResponseHeadersFromFileOrDefault = (specPath) => {
  do {
    specPath = path.dirname(specPath);
    const responseHeadersPath = path.join(specPath, responseHeadersFilename);
    if (fs.existsSync(responseHeadersPath)) {
      return JSONParse(fs.readFileSync(responseHeadersPath, 'utf8'));
    }
  } while (specPath !== specRootPath);

  return defaultResponseHeaders;
};

const getResponseHeaders = (spec, specPath) => {
  spec.responseHeaders = {
    ...getResponseHeadersFromFileOrDefault(specPath),
    ...(spec.responseHeaders ?? {}),
  };
};

const transformValues = (obj) => {
  //TODO use this everywhere below
  const shardRealm = `${systemShard}.${systemRealm}.`;

  if (Array.isArray(obj)) {
    return obj.map(transformValues);
  } else if (typeof obj === 'object' && obj !== null) {
    return Object.fromEntries(
      Object.entries(obj).map(([key, value]) => {
        if (
          (typeof value === 'string' && key.toLowerCase().endsWith('end')) ||
          key.toLowerCase().endsWith('time') ||
          key.toLowerCase() === 'from' ||
          key.toLowerCase().includes('start') ||
          key.toLowerCase().includes('timestamp') ||
          key.toLowerCase() === 'to'
        ) {
          return [key, value]; // leave unchanged
        }
        return [key, transformValues(value)];
      })
    );
  } else if (typeof obj === 'string') {
    if (/^0\.0\.\d+$/.test(obj)) {
      return obj.replace(/^0\.0\./, `${systemShard}.${systemRealm}.`);
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

      entry = entry.replace(/0\.0\.(\d+)/g, `${systemShard}.${systemRealm}.$1`);
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

const getSpecs = async () => {
  const modulePath = getModuleDirname(import.meta);
  specRootPath = path.resolve(path.join(modulePath, '..', 'specs', groupSpecPath));

  const javaTestEnvVar = process.env.REST_JAVA_INCLUDE;
  const javaTestRegex = new RegExp(javaTestEnvVar || 'NONE');
  return (
    await Promise.all(
      walk(specRootPath)
        .filter((f) => f.endsWith('.json') && !f.endsWith(responseHeadersFilename))
        .map(async (f) => {
          const specText = fs.readFileSync(f, 'utf8');
          const spec = f.indexOf('stateproof') > -1 ? JSONParse(specText) : transformValues(JSONParse(specText));
          spec.name = path.basename(f);
          getResponseHeaders(spec, f);

          const key = path.dirname(f).replace(specRootPath, '');
          const specs = [];
          if (spec.matrix) {
            const apply = (await import(path.join(modulePath, spec.matrix))).default;
            specs.push(...apply(spec));
          } else {
            specs.push(spec);
          }
          if (javaTestRegex.test(path.dirname(f))) {
            const restJavaSpecs = specs.map((specCopy) => ({...specCopy, java: true, name: specCopy.name + '-Java'}));
            specs.push(...restJavaSpecs);
          } else if (javaTestEnvVar) {
            return {key, specs: []};
          }

          return {key, specs};
        })
    )
  ).reduce((specMap, {key, specs}) => {
    specMap[key] = specMap[key] ?? [];
    specMap[key].push(...specs);
    return specMap;
  }, {});
};

setupIntegrationTest();

const specs = await getSpecs();

describe(`API specification tests - ${groupSpecPath}`, () => {
  const bucketName = 'hedera-demo-streams';
  const featureSupport = {};
  const s3TestDataRoot = path.join(getModuleDirname(import.meta), '..', 'data', 's3');

  let configOverridden = false;
  let configClone;
  let s3Ops;

  const configS3ForStateProof = (endpoint) => {
    config.stateproof = _.merge(config.stateproof, {
      addressBookHistory: false,
      enabled: true,
      streams: {
        network: 'OTHER',
        cloudProvider: cloudProviders.S3,
        endpointOverride: endpoint,
        region: 'us-east-1',
        bucketName,
      },
    });
  };

  const getTests = (spec) => {
    const tests = spec.tests || [
      {
        url: spec.url,
        urls: spec.urls,
        responseJson: spec.responseJson,
        responseStatus: spec.responseStatus,
      },
    ];
    return _.flatten(
      tests.map((test) => {
        const urls = test.urls || [test.url];
        const {responseJson, responseStatus} = test;
        return urls.map((url) => ({url, responseJson, responseStatus}));
      })
    );
  };

  const hasher = (data) => crypto.createHash('sha256').update(data).digest('hex');

  const loadSqlScripts = async (pathPrefix, sqlScripts) => {
    if (!sqlScripts) {
      return;
    }

    for (const sqlScript of sqlScripts) {
      const sqlScriptPath = path.join(getModuleDirname(import.meta), '..', pathPrefix || '', sqlScript);
      const script = fs.readFileSync(sqlScriptPath, 'utf8');
      logger.debug(`loading sql script ${sqlScript}`);
      await ownerPool.query(script);
    }
  };

  const needsS3 = (specs) => Object.keys(specs).some((dir) => dir.includes('stateproof'));

  const overrideConfig = (override) => {
    if (!override) {
      return;
    }

    _.merge(config, override);
    configOverridden = true;
  };

  const restoreConfig = () => {
    if (configOverridden) {
      _.merge(config, configClone);
      configOverridden = false;
    }
  };

  const runSqlFuncs = async (pathPrefix, sqlFuncs) => {
    if (!sqlFuncs) {
      return;
    }

    for (const sqlFunc of sqlFuncs) {
      // path.join returns normalized path, the sqlFunc is a local js file so add './'
      const func = (await import(`./${path.join('..', pathPrefix || '', sqlFunc)}`)).default;
      logger.debug(`running sql func in ${sqlFunc}`);
      await func.apply(null);
    }
  };

  const setupFeatureSupport = (features) => {
    if (features?.fakeTime) {
      const {fakeTime} = features;
      // If the value is a number, it's epoch seconds
      const now = typeof fakeTime === 'number' ? new Date(fakeTime * 1000) : new Date(fakeTime);
      featureSupport.clock = sinon.useFakeTimers({
        now,
        shouldAdvanceTime: true,
        shouldClearNativeTimers: true,
      });
    }
  };

  const specSetupSteps = async (spec) => {
    const setup = spec.setup;
    overrideConfig(setup.config);
    await integrationDomainOps.setup(setup);
    if (setup.sql) {
      await loadSqlScripts(setup.sql.pathprefix, setup.sql.scripts);
      await runSqlFuncs(setup.sql.pathprefix, setup.sql.funcs);
    }
    if (spec.java) {
      await integrationContainerOps.startRestJavaContainer();
    }
    setupFeatureSupport(setup.features);
  };

  const teardownFeatureSupport = () => {
    if (featureSupport.clock) {
      featureSupport.clock.restore();
      delete featureSupport.clock;
    }
  };

  const transformStateProofResponse = (jsonObj) => {
    const deepBase64Encode = (obj) => {
      if (typeof obj === 'string') {
        return hasher(obj);
      }

      const result = {};
      for (const [k, v] of Object.entries(obj)) {
        if (typeof v === 'string') {
          result[k] = hasher(v);
        } else if (Array.isArray(v)) {
          result[k] = v.map((val) => deepBase64Encode(val));
        } else if (_.isPlainObject(v)) {
          result[k] = deepBase64Encode(v);
        } else {
          result[k] = v;
        }
      }
      return result;
    };

    return deepBase64Encode(jsonObj);
  };

  const uploadFilesToS3 = async (endpoint) => {
    const dataPath = path.join(s3TestDataRoot, bucketName);
    // use fake accessKeyId and secretAccessKey, otherwise upload will fail
    const s3client = new S3({
      credentials: {
        accessKeyId: 'AKIAIOSFODNN7EXAMPLE',
        secretAccessKey: 'wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY',
      },
      endpoint,
      forcePathStyle: true,
      region: 'us-east-1',
    });

    logger.debug(`creating s3 bucket ${bucketName}`);
    await s3client.send(new CreateBucketCommand({Bucket: bucketName}));

    logger.debug('uploading file objects to mock s3 service');
    const s3ObjectKeys = [];
    for (const filePath of walk(dataPath)) {
      const s3ObjectKey = path.relative(dataPath, filePath);
      const fileStream = fs.createReadStream(filePath);
      await s3client.send(
        new PutObjectCommand({
          Bucket: bucketName,
          Key: s3ObjectKey,
          Body: fileStream,
          ACL: 'public-read',
        })
      );
      s3ObjectKeys.push(s3ObjectKey);
    }
    logger.debug(`uploaded ${s3ObjectKeys.length} file objects: ${s3ObjectKeys}`);
  };

  jest.setTimeout(60000);

  beforeAll(async () => {
    if (needsS3(specs)) {
      s3Ops = new IntegrationS3Ops();
      await s3Ops.start();
      configS3ForStateProof(s3Ops.getEndpointUrl());
      await uploadFilesToS3(s3Ops.getEndpointUrl());
    }

    configClone = _.cloneDeep(config);
  }, defaultBeforeAllTimeoutMillis);

  afterAll(async () => {
    if (s3Ops) {
      await s3Ops.stop();
    }
  });

  afterEach(() => {
    restoreConfig();
    teardownFeatureSupport();
  });

  Object.entries(specs).forEach(([dir, specs]) => {
    describe(`${dir}`, () => {
      specs.forEach((spec) => {
        describe(`${spec.name}`, () => {
          getTests(spec).forEach((tt) => {
            test(`${tt.url}`, async () => {
              await specSetupSteps(spec);
              if (spec.postSetup) {
                await spec.postSetup();
              }

              const target = spec.java ? global.REST_JAVA_BASE_URL : server;
              const response = await request(target).get(tt.url);

              expect(response.status).toEqual(tt.responseStatus);
              const contentType = response.get('Content-Type');
              expect(contentType).not.toBeNull();

              if (contentType.includes('application/json')) {
                let jsonObj = response.text === '' ? {} : JSONParse(response.text);
                if (response.status === 200 && dir.endsWith('stateproof')) {
                  jsonObj = transformStateProofResponse(jsonObj);
                }
                expect(jsonObj).toEqual(tt.responseJson);
              } else {
                expect(response.text).toEqual(tt.responseJson);
              }

              if (response.status >= 200 && response.status < 300) {
                expect(lowercaseKeys(response.headers)).toMatchObject(lowercaseKeys(spec.responseHeaders));
              }
            });
          });
        });
      });
    });
  });
});

const lowercaseKeys = (object) => _.mapKeys(object, (v, k) => k.toLowerCase());
