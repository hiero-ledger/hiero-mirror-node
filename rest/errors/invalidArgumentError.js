// SPDX-License-Identifier: Apache-2.0

import RestError from './restError';

const InvalidArgumentErrorMessageFormat = 'Invalid parameter: ';
const invalidParamUsageMessageFormat = 'Invalid parameter usage: ';
const ParameterExceedsMaxErrorMessageFormat = 'Parameter values count exceeds maximum number allowed: ';
const unknownParamMessageFormat = 'Unknown query parameter: ';

class InvalidArgumentError extends RestError {
  static INVALID_ERROR_CODE = 'invalidArgument';
  static PARAM_COUNT_EXCEEDS_MAX_CODE = 'paramCountExceedsMax';
  static INVALID_PARAM_USAGE = 'invalidParamUsage';
  static UNKNOWN_PARAM_USAGE = 'unknownParamUsage';

  constructor(errorMessage) {
    super();
    this.message = errorMessage;
  }

  // factory method to help common case
  static forParams(badParams) {
    if (!Array.isArray(badParams)) {
      badParams = [badParams];
    }
    return new InvalidArgumentError(badParams.map((message) => `${InvalidArgumentErrorMessageFormat}${message}`));
  }

  static forRequestValidation(badParams) {
    if (!Array.isArray(badParams)) {
      badParams = [badParams];
    }

    return new InvalidArgumentError(
      badParams.map((message) => {
        if (message.code === this.PARAM_COUNT_EXCEEDS_MAX_CODE) {
          return `${ParameterExceedsMaxErrorMessageFormat}${message.key} count: ${message.count} max: ${message.max}`;
        } else if (message.code === this.INVALID_PARAM_USAGE) {
          return `${invalidParamUsageMessageFormat}${message.key} - ${message.error}`;
        } else if (message.code === this.UNKNOWN_PARAM_USAGE) {
          return `${unknownParamMessageFormat}${message.key || message}`;
        } else {
          return `${InvalidArgumentErrorMessageFormat}${message.key || message}`;
        }
      })
    );
  }
}

export default InvalidArgumentError;
