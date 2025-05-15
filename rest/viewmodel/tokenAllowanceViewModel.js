// SPDX-License-Identifier: Apache-2.0

import BaseAllowanceViewModel from './baseAllowanceViewModel';
import EntityId from '../entityId';

/**
 * TokenAllowance view model
 */
class TokenAllowanceViewModel extends BaseAllowanceViewModel {
  /**
   * Constructs tokenAllowance view model
   *
   * @param {TokenAllowance} tokenAllowance
   */
  constructor(tokenAllowance) {
    super(tokenAllowance);
    this.token_id = EntityId.parse(tokenAllowance.tokenId).toString();
  }
}

export default TokenAllowanceViewModel;
