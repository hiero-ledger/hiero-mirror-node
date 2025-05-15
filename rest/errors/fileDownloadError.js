// SPDX-License-Identifier: Apache-2.0

import RestError from './restError';

const FileDownloadErrorMessage = 'Failed to download file';

class FileDownloadError extends RestError {
  constructor(errorMessage) {
    super(errorMessage === undefined ? FileDownloadErrorMessage : errorMessage);
  }
}

export default FileDownloadError;
