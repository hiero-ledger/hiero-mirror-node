// SPDX-License-Identifier: Apache-2.0

import {unzipSync} from 'zlib';

class Web3Parser {
  static #INPUT_LINE_REGEX = /^([\d\-TZ:.]+) .* POST (.*) in \d+ ms.*: .*- (.*)$/;

  parse(line) {
    const match = line?.match(Web3Parser.#INPUT_LINE_REGEX);
    if (!match) {
      return null;
    }

    let body = match[3];
    if (!body) {
      return null;
    }

    try {
      if (body[0] !== '{') {
        body = unzipSync(Buffer.from(body, 'base64')).toString();
      }

      if (body[body.length - 1] !== '}') {
        return null;
      }
    } catch (err) {
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
