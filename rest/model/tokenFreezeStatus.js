// SPDX-License-Identifier: Apache-2.0

import {InvalidArgumentError} from '../errors';

class TokenFreezeStatus {
  static STATUSES = ['NOT_APPLICABLE', 'FROZEN', 'UNFROZEN'];

  constructor(id) {
    this._id = Number(id);
    if (Number.isNaN(this._id) || this._id < 0 || this._id > 2) {
      throw new InvalidArgumentError(`Invalid token freeze status id ${id}`);
    }
  }

  getId() {
    return this._id;
  }

  toJSON() {
    return this.toString();
  }

  toString() {
    return TokenFreezeStatus.STATUSES[this._id];
  }
}

export default TokenFreezeStatus;
