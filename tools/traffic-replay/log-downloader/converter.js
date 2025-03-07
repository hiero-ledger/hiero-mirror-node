// SPDX-License-Identifier: Apache-2.0

import fs from 'fs';

class GoReplayConverter {
  static #CRLF = '\r\n';
  static #HTTP_HEADERS = ['Host: 127.0.0.1:80', 'User-Agent: curl/8.8.0', 'Accept: */*', GoReplayConverter.#CRLF].join(
    GoReplayConverter.#CRLF
  );
  static #INPUT_LINE_REGEX = /^([\d\-TZ:.]+) .* GET (.*) in \d+ ms: .*$/;
  static #LOG_INTERVAL = 5; // log every 5 seconds
  static #PAYLOAD_SEPARATOR = '\n🐵🙈🙉\n';
  static #LOG_SUFFIX = `${GoReplayConverter.#HTTP_HEADERS}${GoReplayConverter.#PAYLOAD_SEPARATOR}`; // just hardcode it
  static #PAYLOAD_TYPE = 1; // request
  static #REQUEST_DURATION = 0; // hardcode it to 0 since it's not used in replay

  #count = 0;
  #lastLogTimestamp = 0;
  #lastStatSeconds;
  #startSeconds;
  #outputStream;

  constructor(outputFile) {
    this.#outputStream = fs.createWriteStream(outputFile);
  }

  accept(line) {
    if (!this.#startSeconds) {
      this.#startSeconds = getEpochSeconds();
      this.#lastStatSeconds = this.#startSeconds;
    }

    const converted = this.#convertLine(line);
    if (!converted) {
      return;
    }

    this.#outputStream.write(converted);
    this.#recordOne();
  }

  close() {
    this.#outputStream.close();
    this.#showStat(true);
  }

  #convertLine(line) {
    const match = line?.match(GoReplayConverter.#INPUT_LINE_REGEX);
    if (!match) {
      return null;
    }

    const epochMs = new Date(match[1]).getTime();
    const requestUrl = match[2];
    // Logs are gathered from multiple pods in a distributed fashion, so it's possible that the timestamps from the
    // ordered logs are out of order. Workaround it by make sure the timestamp doesn't go backwards and the impact to
    // traffic replay is negligible.
    const logTimestamp = epochMs > this.#lastLogTimestamp ? epochMs : this.#lastLogTimestamp;
    this.#lastLogTimestamp = logTimestamp;
    // The time in rest api log is at millis granularity, but goreplay requires nanos, so just suffix with 000000
    return `${GoReplayConverter.#PAYLOAD_TYPE} ${getUUID()} ${logTimestamp}000000 ${
      GoReplayConverter.#REQUEST_DURATION
    }\nGET ${requestUrl} HTTP/1.1${GoReplayConverter.#CRLF}${GoReplayConverter.#LOG_SUFFIX}`;
  }

  #recordOne() {
    this.#count++;
    if (getElapsed(this.#lastStatSeconds) >= GoReplayConverter.#LOG_INTERVAL) {
      this.#lastStatSeconds = getEpochSeconds();
      this.#showStat();
    }
  }

  #showStat(final = false) {
    const elapsed = getElapsed(this.#startSeconds);
    const rate = this.#count / elapsed;
    const prefix = final ? 'Completed processing of' : 'Processed';
    log(`${prefix} ${this.#count} lines in ${toThousandth(elapsed)} seconds at average rate of ${toThousandth(rate)}`);
  }
}

const getElapsed = (lastSeconds) => getEpochSeconds() - lastSeconds;

const getEpochSeconds = () => Date.now() / 1000;

const getUUID = () => Buffer.from(Array.from({length: 12}, randomByte)).toString('hex');

const randomByte = () => Math.floor(Math.random() * 256);

const toThousandth = (value) => value.toFixed(3);

export default GoReplayConverter;
