// SPDX-License-Identifier: Apache-2.0

import RestError from './restError';

const FileDecodeErrorMessage =
  'Failed to decode file contents. Ensure timestamp filters cover the complete file create/update and append transactions';

class FileDecodeError extends RestError {
  constructor(errorMessage) {
    let message = FileDecodeErrorMessage;
    if (errorMessage !== undefined) {
      message += `. Error: '${errorMessage}'`;
    }

    super(message);
  }
}

export default FileDecodeError;
