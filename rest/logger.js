// SPDX-License-Identifier: Apache-2.0

import httpContext from 'express-http-context';
import * as constants from './constants';

class Level {
  static TRACE = 0;
  static DEBUG = 1;
  static INFO = 2;
  static WARN = 3;
  static ERROR = 4;

  static NAMES = new Map(Object.entries(Level).map(([k, v]) => [v, k]));

  static of(level) {
    return Level[level.toUpperCase()];
  }

  static toString(level) {
    return Level.NAMES.get(level);
  }
}

class Logger {
  #bufferSize;
  #buffer;
  #flushTimer;
  #level;
  #position;

  constructor({level = 'INFO', bufferSize = 4096} = {}) {
    this.#bufferSize = bufferSize;
    this.#buffer = Buffer.allocUnsafe(bufferSize);
    this.#flushTimer = setInterval(() => this.#flush(), 250).unref();
    this.#position = 0;
    this.setLevel(level);

    process.on('exit', () => this.#flush());

    process.on('SIGINT', () => {
      this.#flush();
      process.exit(0);
    });

    process.on('SIGTERM', () => {
      this.#flush();
      process.exit(0);
    });
  }

  #flush() {
    if (this.#position === 0) {
      return;
    }

    process.stdout.write(this.#buffer.subarray(0, this.#position));
    this.#position = 0;
  }

  #log(level, msg, err) {
    if (level < this.#level) {
      return;
    }

    const time = new Date().toISOString();
    const levelName = Level.toString(level);
    const requestId = httpContext.get(constants.requestIdLabel) || 'Startup';
    const stack = err?.stack ? `\n${err.stack}` : '';
    const text = `${time} ${levelName} ${requestId} ${msg}${stack}\n`;
    this.#write(text);
  }

  #write(line) {
    const bytes = Buffer.byteLength(line);

    if (this.#position + bytes > this.#bufferSize) {
      this.#flush();
    }

    // Line is larger than entire buffer so write directly
    if (bytes > this.#bufferSize) {
      process.stdout.write(line);
      return;
    }

    this.#buffer.write(line, this.#position);
    this.#position += bytes;
  }

  isTraceEnabled() {
    return this.#level <= Level.TRACE;
  }

  isDebugEnabled() {
    return this.#level <= Level.DEBUG;
  }

  trace(msg, err) {
    this.#log(Level.TRACE, msg, err);
  }

  debug(msg, err) {
    this.#log(Level.DEBUG, msg, err);
  }

  info(msg, err) {
    this.#log(Level.INFO, msg, err);
  }

  warn(msg, err) {
    this.#log(Level.WARN, msg, err);
  }

  error(msg, err) {
    this.#log(Level.ERROR, msg, err);
  }

  setLevel(level = 'INFO') {
    this.#level = Level.of(level) ?? Level.INFO;
  }
}

global.logger = new Logger();

export default Logger;
