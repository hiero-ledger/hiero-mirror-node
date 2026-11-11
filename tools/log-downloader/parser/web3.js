// SPDX-License-Identifier: Apache-2.0

import {unzipSync} from 'zlib';

import {MAX_BODY_LENGTH} from '../constants.js';

class Web3Parser {
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

    try {
      if (body[0] !== '{') {
        body = unzipSync(Buffer.from(body, 'base64'), {maxOutputLength: MAX_BODY_LENGTH}).toString();
      }

      if (body[body.length - 1] !== '}') {
        return null;
      }
    } catch {
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
}

export default Web3Parser;
