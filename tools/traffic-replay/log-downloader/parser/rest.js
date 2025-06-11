// SPDX-License-Identifier: Apache-2.0

class RestParser {
  static #INPUT_LINE_REGEX = /^([\d\-TZ:.]+) .* GET (.*) in \d+ ms: .*$/;

  parse(line) {
    const match = line?.match(RestParser.#INPUT_LINE_REGEX);
    if (!match) {
      return null;
    }

    return {
      timestamp: new Date(match[1]).getTime(),
      url: match[2],
      verb: 'GET',
    };
  }
}

export default RestParser;
