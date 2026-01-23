// SPDX-License-Identifier: Apache-2.0

import {httpStatusCodes, requestStartTime, StatusCode} from '../constants';
import {HttpError} from 'http-errors';
import RestError from '../errors/restError';

const defaultStatusCode = httpStatusCodes.INTERNAL_ERROR;

const errorMap = {
  DbError: httpStatusCodes.SERVICE_UNAVAILABLE,
  FileDownloadError: httpStatusCodes.BAD_GATEWAY,
  InvalidArgumentError: httpStatusCodes.BAD_REQUEST,
  NotFoundError: httpStatusCodes.NOT_FOUND,
  RangeError: httpStatusCodes.BAD_REQUEST,
};

const simpleErrors = /statement timeout/;

// Error middleware which formats thrown errors and maps them to appropriate http status codes
// next param is required to ensure express maps to this middleware and can also be used to pass onto future middleware
const handleError = async (err, req, res, next) => {
  var statusCode = defaultStatusCode;

  if (err.constructor.name in errorMap) {
    statusCode = errorMap[err.constructor.name];
  } else if (err instanceof HttpError) {
    statusCode = new StatusCode(err.statusCode, err.msg);
  }

  let errorMessage;
  const startTime = res.locals[requestStartTime];
  const elapsed = startTime ? Date.now() - startTime : 0;

  if (shouldReturnMessage(statusCode)) {
    errorMessage = err.message;
    logger.warn(
      `${req.ip} ${req.method} ${req.originalUrl} in ${elapsed} ms: ${statusCode} ${err.constructor.name} ${errorMessage}`
    );
  } else {
    errorMessage = statusCode.message;
    const detailedMessage = shouldPrintStacktrace(err) ? err : err.message;
    logger.error(`${req.ip} ${req.method} ${req.originalUrl} in ${elapsed} ms: ${statusCode}`, detailedMessage);
  }

  res.status(statusCode.code).json(errorMessageFormat(errorMessage));
};

const handleRejection = (reason, promise) => {
  logger.warn(`Unhandled rejection at:${promise} reason: ${reason}`);
};

const handleUncaughtException = (err) => {
  if (err instanceof RestError) {
    logger.error('Unhandled exception:', err);
  } else {
    throw err;
  }
};

const shouldReturnMessage = (statusCode) => {
  return statusCode.isClientError() || statusCode === httpStatusCodes.BAD_GATEWAY;
};

/**
 * Returns if the stacktrace should be logged.
 * @param errorMessage
 * @returns {boolean}
 */
const shouldPrintStacktrace = (err) => {
  return !simpleErrors.test(err.message);
};

/**
 * Application error message format
 * @param errorMessages array of messages
 * @returns {{_status: {messages: *}}}
 */
const errorMessageFormat = (errorMessages) => {
  if (!Array.isArray(errorMessages)) {
    errorMessages = [errorMessages];
  }

  return {
    _status: {
      messages: errorMessages.map((m) => {
        const response = m.detail ? {message: m.message, detail: m.detail} : {message: m};
        if (typeof m === 'object' && 'data' in m) {
          response['data'] = m.data;
        }
        return response;
      }),
    },
  };
};

export {handleError, handleRejection, handleUncaughtException};
