// SPDX-License-Identifier: Apache-2.0

class Logger {
  static LEVELS = {TRACE: 0, DEBUG: 1, INFO: 2, WARN: 3, ERROR: 4};
  static NAMES = Object.fromEntries(Object.entries(Logger.LEVELS).map(([k, v]) => [v, k]));

  #level;

  constructor(level = 'info') {
    this.#level = Logger.LEVELS[level.toLowerCase()] ?? Logger.LEVELS.INFO;
  }

  #log(level, msg, err) {
    if (level < this.#level) {
      return;
    }

    const time = new Date().toISOString();
    const levelName = Logger.NAMES[level];
    const stack = err?.stack ? `\n${err.stack}` : '';
    const text = `${time} ${levelName} ${msg}${stack}\n`;
    process.stdout.write(text);
  }

  info(msg, err) {
    this.#log(Logger.LEVELS.INFO, msg, err);
  }

  warn(msg, err) {
    this.#log(Logger.LEVELS.WARN, msg, err);
  }

  error(msg, err) {
    this.#log(Logger.LEVELS.ERROR, msg, err);
  }
}

global.logger = new Logger('info');

export default logger;
