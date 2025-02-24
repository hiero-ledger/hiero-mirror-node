// SPDX-License-Identifier: Apache-2.0

const InvalidConfigErrorMessage = 'Invalid config';

class InvalidConfigError extends Error {
  constructor(errorMessage) {
    super();
    this.message = errorMessage === undefined ? InvalidConfigErrorMessage : errorMessage;
  }
}

export default InvalidConfigError;
