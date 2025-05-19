// SPDX-License-Identifier: Apache-2.0

import {GetObjectCommand, S3} from '@aws-sdk/client-s3';
import {NodeHttpHandler} from '@smithy/node-http-handler';
import https from 'https';

import config from './config';
import {cloudProviders, defaultCloudProviderEndpoints} from './constants';

class S3Client {
  constructor(s3, hasCredentials, gcpProjectId) {
    this.s3 = s3;
    this.hasCredentials = hasCredentials;
    this.gcpProjectId = gcpProjectId;
    this.httpRequest = null;

    this.s3.middlewareStack.add(
      (next, context) => (args) => {
        if (gcpProjectId) {
          args.request.query.userProject = this.gcpProjectId;
        }
        this.httpRequest = args.request;
        return next(args);
      },
      {
        step: 'build',
      }
    );
  }

  getHttpRequest() {
    return this.httpRequest;
  }

  getObject(params, abortSignal) {
    return this.s3.send(new GetObjectCommand(params), {abortSignal});
  }

  getConfig() {
    return this.s3.config;
  }

  getHasCredentials() {
    return this.hasCredentials;
  }
}

const buildS3ConfigFromStreamsConfig = () => {
  const {accessKey, cloudProvider, endpointOverride, gcpProjectId, httpOptions, maxRetries, secretKey, region} =
    config.stateproof.streams;
  const hasEndpointOverride = !!endpointOverride;
  const isGCP = cloudProvider === cloudProviders.GCP;

  const endpoint = hasEndpointOverride ? endpointOverride : defaultCloudProviderEndpoints[cloudProvider];
  const forcePathStyle = hasEndpointOverride || isGCP;
  const requestHandler = new NodeHttpHandler({
    httpsAgent: new https.Agent({
      keepAlive: true,
    }),
    connectionTimeout: httpOptions.connectTimeout,
    requestTimeout: httpOptions.timeout,
  });

  const s3Config = {
    credentials: {
      accessKeyId: '',
      secretAccessKey: '',
    },
    endpoint,
    forcePathStyle,
    maxAttempts: maxRetries + 1,
    region,
    requestHandler,
  };

  if (!!accessKey && !!secretKey) {
    logger.debug('Building s3Config with provided access/secret key');
    s3Config.credentials.accessKeyId = accessKey;
    s3Config.credentials.secretAccessKey = secretKey;
  } else {
    logger.debug('Building s3Config with no credentials');
  }

  return {
    s3Config,
    gcpProjectId: isGCP ? gcpProjectId : null,
  };
};

/**
 * Create a S3 client with configuration from config object.
 * @returns {S3Client}
 */
const createS3Client = () => {
  const {s3Config, gcpProjectId} = buildS3ConfigFromStreamsConfig();
  return new S3Client(new S3(s3Config), !!s3Config.credentials.accessKeyId, gcpProjectId);
};

export default {
  createS3Client,
};
