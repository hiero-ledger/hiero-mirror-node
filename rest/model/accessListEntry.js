// SPDX-License-Identifier: Apache-2.0

import * as utils from '../utils.js';

class AccessListEntry {
  constructor(accessListEntry) {
    this.address = utils.toHexString(utils.stripHexPrefix(accessListEntry.address), true, 40);
    const storageKeys = accessListEntry.storage_keys ?? accessListEntry.storageKeys ?? [];
    this.storage_keys = storageKeys.map((key) => utils.toHexString(utils.stripHexPrefix(key), true, 64));
  }

  static ADDRESS = `address`;
  static STORAGE_KEYS = `storage_keys`;
}

export default AccessListEntry;
