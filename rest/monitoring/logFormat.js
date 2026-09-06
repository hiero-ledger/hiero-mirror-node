// SPDX-License-Identifier: Apache-2.0

const CONTROL_CHARACTERS = /[\p{Cc}]/gu;

const sanitize = (value) => (value == null ? value : String(value).replace(CONTROL_CHARACTERS, '_'));

const formatRequestLog = (req, httpCode, passed, total) =>
  sanitize(`${req.ip} ${req.method} ${req.originalUrl} returned ${httpCode}: ${passed}/${total} tests passed`);

export {formatRequestLog, sanitize};
