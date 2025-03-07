// SPDX-License-Identifier: Apache-2.0

import config from '../config';
import {cloudProviders, defaultCloudProviderEndpoints} from '../constants';
import s3client from '../s3client';
import {jest} from '@jest/globals';

const defaultValidStreamsConfig = {
  accessKey: 'testAccessKey',
  cloudProvider: cloudProviders.S3,
  bucketName: 'testBucket',
  httpOptions: {
    connectTimeout: 12,
    timeout: 15,
  },
  maxRetries: 1,
  region: 'us-east-3',
  secretKey: 'testSecretKey',
};

beforeEach(() => {
  config.stateproof = {
    streams: {...defaultValidStreamsConfig},
  };
});

const overrideStreamsConfig = (override) => {
  config.stateproof.streams = Object.assign(config.stateproof.streams, override);
};

describe('createS3Client with valid config', () => {
  const verifyForSuccess = async (streamsConfig, s3Client) => {
    expect(s3Client).toBeTruthy();

    const s3Config = s3Client.getConfig();
    const endpoint = await s3Config.endpoint();
    let endpointUrl = `${endpoint.protocol}//${endpoint.hostname}`;
    if (endpoint.port) {
      endpointUrl += `:${endpoint.port}`;
    }

    if (streamsConfig.endpointOverride) {
      expect(endpointUrl).toEqual(streamsConfig.endpointOverride);
    } else if (streamsConfig.cloudProvider === cloudProviders.S3) {
      expect(endpointUrl).toEqual(defaultCloudProviderEndpoints.S3);
    } else {
      expect(endpointUrl).toEqual(defaultCloudProviderEndpoints.GCP);
    }

    const expectedRegion = streamsConfig.region ? streamsConfig.region : 'us-east-1';
    expect(s3Config.region()).resolves.toEqual(expectedRegion);

    expect(s3Config.requestHandler.configProvider).resolves.toMatchObject({
      connectionTimeout: streamsConfig.httpOptions.connectTimeout,
      requestTimeout: streamsConfig.httpOptions.timeout,
    });

    if (!streamsConfig.accessKey || !streamsConfig.secretKey) {
      expect(s3Client.getHasCredentials()).toBeFalsy();
    } else {
      expect(s3Client.getHasCredentials()).toBeTruthy();
      expect(s3Config.credentials()).resolves.toMatchObject({
        accessKeyId: streamsConfig.accessKey,
        secretAccessKey: streamsConfig.secretKey,
      });
    }

    const abortController = new AbortController();
    abortController.abort();
    const commandParams = {
      Bucket: 'demo-bucket',
      Key: 'foobar/foobar.txt',
    };

    try {
      await s3Client.getObject(commandParams, abortController.signal);
    } catch (err) {
      //
    }

    const httpRequest = s3Client.getHttpRequest();
    if (streamsConfig.cloudProvider === cloudProviders.GCP && streamsConfig.gcpProjectId) {
      expect(httpRequest.query).toMatchObject({userProject: `${streamsConfig.gcpProjectId}`});
    } else {
      expect(httpRequest.query).not.toMatchObject({userProject: `${streamsConfig.gcpProjectId}`});
    }
  };

  const testSpecs = [
    {
      name: 'default valid config',
    },
    {
      name: 'valid config with cloudProvider S3',
      override: {cloudProvider: cloudProviders.S3},
    },
    {
      name: 'valid config with cloudProvider S3 with gcpProjectId accidentally set',
      override: {cloudProvider: cloudProviders.S3, gcpProjectId: 'demoS3Project'},
    },
    {
      name: 'valid config with cloudProvider gcp',
      override: {cloudProvider: cloudProviders.GCP},
    },
    {
      name: 'valid config with cloudProvider gcp and gcpProjectId',
      override: {cloudProvider: cloudProviders.GCP, gcpProjectId: 'demoGcpProject'},
    },
    {
      name: 'valid config with region "us-west-1"',
      override: {region: 'us-west-1'},
    },
    {
      name: 'valid config with endpointOverride',
      override: {endpointOverride: 'https://s3.us-east-2.amazonaws.com'},
    },
    {
      name: 'valid config with null accessKey',
      override: {accessKey: null},
    },
    {
      name: 'valid config with empty accessKey',
      override: {accessKey: ''},
    },
    {
      name: 'valid config with null secretKey',
      override: {secretKey: null},
    },
    {
      name: 'valid config with empty secretKey',
      override: {secretKey: ''},
    },
    {
      name: 'valid config with maxRetries 3',
      override: {maxRetries: 3},
    },
    {
      name: 'valid config with updated httpOptions',
      override: {httpOptions: {connectTimeout: 10, timeout: 50}},
    },
  ];

  jest.setTimeout(60000);
  testSpecs.forEach((spec) => {
    test(spec.name, async () => {
      overrideStreamsConfig(spec.override);
      const s3Client = s3client.createS3Client();
      await verifyForSuccess(config.stateproof.streams, s3Client);
    });
  });
});

describe('createS3Client with invalid regions', () => {
  const verifyInvalid = async (streamsConfig, s3Client) => {
    const s3Config = s3Client.getConfig();
    await expect(s3Config.region()).rejects.toThrow('Region is missing');

    const abortController = new AbortController();
    abortController.abort();
    const commandParams = {
      Bucket: 'demo-bucket',
      Key: 'foobar/foobar.txt',
    };
    const request = s3Client.getObject(commandParams, abortController.signal);
    await expect(request).rejects.toThrow('Region is missing');
  };

  test('null region', async () => {
    overrideStreamsConfig({region: null});
    const s3Client = s3client.createS3Client();
    await verifyInvalid(config.stateproof.streams, s3Client);
  });

  test('empty region', async () => {
    overrideStreamsConfig({region: ''});
    expect(() => {
      s3client.createS3Client();
    }).toThrow('Region is missing');
  });
});
