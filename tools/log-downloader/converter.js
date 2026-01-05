// SPDX-License-Identifier: Apache-2.0

import fs from 'fs';

import {RestParser, Web3Parser} from './parser/index.js';

class GoReplayConverter {
  static #CRLF = '\r\n';
  static #HTTP_HEADERS = ['Host: 127.0.0.1:80', 'User-Agent: curl/8.8.0', 'Accept: */*'].join(GoReplayConverter.#CRLF);
  static #LOG_INTERVAL = 5; // log every 5 seconds
  static #PAYLOAD_SEPARATOR = '🐵🙈🙉\n';
  static #PAYLOAD_TYPE = 1; // request
  static #REQUEST_DURATION = 0; // hardcode it to 0 since it's not used in replay

  #count = 0;
  #lastLogTimestamp = 0;
  #lastStatSeconds;
  #startSeconds;
  #outputStream;
  #parser;

  constructor(outputFile, service) {
    this.#outputStream = fs.createWriteStream(outputFile);
    this.#parser = service === 'rest' ? new RestParser() : new Web3Parser();
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
    const request = this.#parser.parse(line);
    if (!request) {
      return null;
    }

    const {timestamp} = request;
    // Logs are gathered from multiple pods in a distributed fashion, so it's possible that the timestamps from the
    // ordered logs are out of order. Workaround it by make sure the timestamp doesn't go backwards and the impact to
    // traffic replay is negligible.
    const logTimestamp = timestamp > this.#lastLogTimestamp ? timestamp : this.#lastLogTimestamp;
    this.#lastLogTimestamp = logTimestamp;
    const body = `${request.body ?? ''}\n`;
    const headers = [GoReplayConverter.#HTTP_HEADERS, ...(request.headers ?? [])].join(GoReplayConverter.#CRLF);
    // Timestamp is in millis, but goreplay requires nanos, so just suffix with 000000
    const logTimestampNs = `${logTimestamp}000000`;
    return (
      `${GoReplayConverter.#PAYLOAD_TYPE} ${getUUID()} ${logTimestampNs} ${GoReplayConverter.#REQUEST_DURATION}\n` +
      `${request.verb} ${request.url} HTTP/1.1${GoReplayConverter.#CRLF}` +
      `${headers}${GoReplayConverter.#CRLF}` +
      GoReplayConverter.#CRLF +
      body +
      GoReplayConverter.#PAYLOAD_SEPARATOR
    );
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
