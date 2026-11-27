// SPDX-License-Identifier: Apache-2.0

import {unzipSync} from 'zlib';

import {MAX_BODY_LENGTH} from '../constants.js';

class Web3Parser {
  static #BASE64_REGEX = /^[A-Za-z0-9+/]+={0,2}$/;
  // Sanity cap only: base64 of MAX_BODY_LENGTH bytes plus 1 KiB for gzip framing.
  static #MAX_ENCODED_LENGTH = Math.ceil(MAX_BODY_LENGTH / 3) * 4 + 1024;
  // Smallest possible zlib stream: 2 byte header, 2 byte empty deflate block, 4 byte Adler-32 checksum
  static #MIN_ENCODED_LENGTH = 12;
  // Only the short "head" of the line is run through the regex; the body is located with indexOf. This bounds
  // regex backtracking regardless of line length. Real heads (timestamp, pod/ip, url, duration, status) are far smaller.
  static #MAX_HEAD_LENGTH = 1024;
  static #INPUT_LINE_REGEX = /^([\d\-TZ:.]+) .* POST (.*) in \d+ ms.*?: /;
  static #LINE_TERMINATOR_REGEX = /[\n\r\u2028\u2029]/;

  parse(line) {
    if (!line || Web3Parser.#LINE_TERMINATOR_REGEX.test(line)) {
      return null;
    }

    const match = line.slice(0, Web3Parser.#MAX_HEAD_LENGTH).match(Web3Parser.#INPUT_LINE_REGEX);
    if (!match) {
      return null;
    }

    // First separator after the head, so '- ' inside the body is left intact
    const bodyStart = line.indexOf('- ', match[0].length);
    if (bodyStart < 0) {
      return null;
    }

    let body = line.slice(bodyStart + 2);
    if (!body) {
      return null;
    }

    if (body[0] !== '{') {
      body = Web3Parser.#decompress(body);
    }

    if (!body || body[body.length - 1] !== '}') {
      return null;
    }

    return {
      body,
      headers: ['Content-Type: application/json', `Content-Length: ${body.length}`],
      timestamp: new Date(match[1]).getTime(),
      url: match[2],
      verb: 'POST',
    };
  }

  // Returns the decompressed body, or null if the input is implausible, malformed, or would inflate past MAX_BODY_LENGTH.
  static #decompress(encoded) {
    if (
      encoded.length < Web3Parser.#MIN_ENCODED_LENGTH ||
      encoded.length > Web3Parser.#MAX_ENCODED_LENGTH ||
      encoded.length % 4 !== 0 ||
      !Web3Parser.#BASE64_REGEX.test(encoded)
    ) {
      return null;
    }

    try {
      return unzipSync(Buffer.from(encoded, 'base64'), {maxOutputLength: MAX_BODY_LENGTH}).toString();
    } catch {
      // Includes ERR_BUFFER_TOO_LARGE / RangeError when the output would exceed maxOutputLength
      return null;
    }
  }
}

export default Web3Parser;
