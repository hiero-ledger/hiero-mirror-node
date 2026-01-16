// SPDX-License-Identifier: Apache-2.0

import config from '../config';
import {
  contentTypeHeader,
  requestPathLabel,
  requestStartTime,
  responseBodyLabel,
  responseDataLabel,
  responseHeadersLabel,
} from '../constants';
import {NotFoundError} from '../errors';
import {JSONStringify} from '../utils';

const {
  response: {headers},
} = config;

const APPLICATION_JSON = 'application/json; charset=utf-8';
const LINK_NEXT_HEADER = 'Link';
const linkNextHeaderValue = (linksNext) => `<${linksNext}>; rel="next"`;

// Response middleware that pulls response data passed through request and sets in response.
// Next param is required to ensure express maps to this middleware and can also be used to pass onto future middleware
const responseHandler = async (req, res, next) => {
  const responseData = res.locals[responseDataLabel];
  if (responseData === undefined) {
    // unmatched route will have no response data, pass NotFoundError to next middleware
    throw new NotFoundError();
  }

  const path = res.locals[requestPathLabel] ?? req.route.path;
  const mergedHeaders = {
    ...headers.default,
    ...(headers.path[path] ?? {}),
    ...(res.locals[responseHeadersLabel] ?? {}),
  };
  res.set(mergedHeaders);

  const code = res.locals.statusCode;
  const linksNext = res.locals.responseData.links?.next;
  res.status(code);

  if (linksNext) {
    res.set(LINK_NEXT_HEADER, linkNextHeaderValue(linksNext));
  }
  const contentType = res.get(contentTypeHeader);

  res.locals[responseBodyLabel] = contentType === APPLICATION_JSON ? JSONStringify(responseData) : responseData;
  res.send(res.locals[responseBodyLabel]);

  const startTime = res.locals[requestStartTime];
  const elapsed = startTime ? Date.now() - startTime : 0;
  logger.info(`${req.ip} ${req.method} ${req.originalUrl} in ${elapsed} ms: ${code}`);

  next();
};

export default responseHandler;
