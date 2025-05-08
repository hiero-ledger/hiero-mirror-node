// SPDX-License-Identifier: Apache-2.0

import {InvalidArgumentError} from '../errors';

class TokenType {
  static TYPE = ['FUNGIBLE_COMMON', 'NON_FUNGIBLE_UNIQUE'];

  constructor(id) {
    this._id = Number(id);
    if (Number.isNaN(this._id) || this._id < 0 || this._id > 2) {
      throw new InvalidArgumentError(`Invalid token type id ${id}`);
    }
  }

  getId() {
    return this._id;
  }

  toJSON() {
    return this.toString();
  }

  toString() {
    return TokenType.TYPE[this._id];
  }
}

export default TokenType;
