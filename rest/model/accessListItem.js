// SPDX-License-Identifier: Apache-2.0

import * as utils from '../utils.js';

class AccessListItem {
  /**
   * Parses access list item from element in ethereum_transaction.access_list jsonb column
   */
  constructor(accessListItem) {
    this.address = utils.toHexString(utils.stripHexPrefix(accessListItem.address), true, 40);
    this.storage_keys = accessListItem.storage_keys?.map((key) =>
      utils.toHexString(utils.stripHexPrefix(key), true, 64)
    );
  }

  static ADDRESS = `address`;
  static STORAGE_KEYS = `storage_keys`;
}

export default AccessListItem;
