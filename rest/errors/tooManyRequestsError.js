// SPDX-License-Identifier: Apache-2.0

import RestError from './restError';

const TooManyRequestsErrorMessage = 'Too many requests';

class TooManyRequestsError extends RestError {
  constructor(errorMessage) {
    super();
    this.message = errorMessage === undefined ? TooManyRequestsErrorMessage : errorMessage;
  }
}

export default TooManyRequestsError;
