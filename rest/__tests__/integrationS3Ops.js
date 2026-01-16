// SPDX-License-Identifier: Apache-2.0

import {GenericContainer, Wait} from 'testcontainers';
import {isDockerInstalled} from './integrationUtils';

const imageName = 'adobe/s3mock';
const imageTag = 'latest';
const defaultS3Port = 9090;

class IntegrationS3Ops {
  async start() {
    const isInstalled = await isDockerInstalled();
    if (!isInstalled) {
      throw new Error('docker is not installed, cannot start s3mock container');
    }

    const image = `${imageName}:${imageTag}`;
    logger.info(`Starting docker container with image ${image}`);
    const container = await new GenericContainer(image)
      .withExposedPorts(defaultS3Port)
      .withStartupTimeout(180000)
      .withWaitStrategy(Wait.forHttp('/', defaultS3Port))
      .start();
    logger.info('Started dockerized s3mock');
    this.container = container;
    this.hostname = 'localhost';
    this.port = container.getMappedPort(defaultS3Port);
  }

  async stop() {
    if (this.container) {
      await this.container.stop();
    }
  }

  getEndpointUrl() {
    return `http://${this.hostname}:${this.port}`;
  }
}

export default IntegrationS3Ops;
