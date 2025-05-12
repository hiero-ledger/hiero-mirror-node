// SPDX-License-Identifier: Apache-2.0

import {InvalidArgumentError} from '../errors';

class TokenKycStatus {
  static STATUSES = ['NOT_APPLICABLE', 'GRANTED', 'REVOKED'];

  constructor(id) {
    this._id = Number(id);
    if (Number.isNaN(this._id) || this._id < 0 || this._id > 2) {
      throw new InvalidArgumentError(`Invalid token kyc status id ${id}`);
    }
  }

  getId() {
    return this._id;
  }

  toJSON() {
    return this.toString();
  }

  toString() {
    return TokenKycStatus.STATUSES[this._id];
  }
}

export default TokenKycStatus;
