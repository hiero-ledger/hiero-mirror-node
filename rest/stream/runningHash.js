// SPDX-License-Identifier: Apache-2.0

import crypto from 'crypto';

/**
 * Calculates the next running hash from running hash object and next hash object using the specified digest algorithm.
 *
 * @param {object} runningHashObject
 * @param {object} nextHashObject
 * @param {string} algorithm
 * @returns {Buffer} the running hash
 */
const calculateRunningHash = (runningHashObject, nextHashObject, algorithm) => {
  return crypto
    .createHash(algorithm)
    .update(runningHashObject.header)
    .update(runningHashObject.hash)
    .update(nextHashObject.header)
    .update(nextHashObject.hash)
    .digest();
};

export {calculateRunningHash};
