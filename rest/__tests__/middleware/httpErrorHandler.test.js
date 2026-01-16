// SPDX-License-Identifier: Apache-2.0

import {
  DbError,
  FileDecodeError,
  FileDownloadError,
  InvalidArgumentError,
  InvalidClauseError,
  InvalidConfigError,
  NotFoundError,
} from '../../errors';

import {handleUncaughtException} from '../../middleware/httpErrorHandler';

describe('Server error handler', () => {
  test('Throws Error for non rest error', () => {
    const exception = () => handleUncaughtException(new InvalidConfigError('Bad Config'));
    expect(exception).toThrow(InvalidConfigError);
  });

  test('Does not throw error for rest error', () => {
    const exception = () => {
      handleUncaughtException(new DbError());
      handleUncaughtException(new FileDecodeError());
      handleUncaughtException(new FileDownloadError());
      handleUncaughtException(new InvalidArgumentError());
      handleUncaughtException(new InvalidClauseError());
      handleUncaughtException(new NotFoundError());
    };

    expect(exception).not.toThrow(Error);
  });
});
