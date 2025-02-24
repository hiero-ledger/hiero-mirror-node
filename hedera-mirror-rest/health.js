// SPDX-License-Identifier: Apache-2.0

import {DbError, NotFoundError} from './errors';

const readinessQuery = 'select true from address_book limit 1';

/**
 * Allows for a graceful shutdown.
 *
 * @returns {Promise<*>}
 */
const onShutdown = async () => {
  logger.info(`Closing connection pool`);
  return pool.end();
};

/**
 * Function to determine readiness of application.
 *
 * @returns {Promise<void>}
 */
const readinessCheck = async () => {
  return pool
    .query(readinessQuery)
    .catch((err) => {
      throw new DbError(err.message);
    })
    .then((results) => {
      if (results.rowCount !== 1) {
        throw new NotFoundError('Application readiness check failed');
      }
    });
};

/**
 * Function to determine liveness of application.
 *
 * @returns {Promise<void>}
 */
const livenessCheck = async () => {};

export default {
  onShutdown,
  livenessCheck,
  readinessCheck,
};
