// SPDX-License-Identifier: Apache-2.0

import RestError from './restError';

const InvalidClauseErrorMessage = 'Invalid clause produced after parsing query parameters';

class InvalidClauseError extends RestError {
  constructor(errorMessage) {
    super(errorMessage === undefined ? InvalidClauseErrorMessage : errorMessage);
  }
}

export default InvalidClauseError;
