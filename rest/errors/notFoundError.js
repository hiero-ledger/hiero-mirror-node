// SPDX-License-Identifier: Apache-2.0

import RestError from './restError';

const NotFoundErrorMessage = 'Not found';

class NotFoundError extends RestError {
  constructor(errorMessage, data = null, detail = null) {
    super(errorMessage === undefined ? NotFoundErrorMessage : errorMessage, data, detail);
  }
}

export default NotFoundError;
