// SPDX-License-Identifier: Apache-2.0

import EntityId from '../entityId';
import {nsToSecNs} from '../utils';

/**
 * BaseAllowance view model, captures the common fields of the allowance view model classes
 */
class BaseAllowanceViewModel {
  /**
   * Constructs base allowance view model
   *
   * @param {{owner: string, spender: string, amount: long, amountGranted: long, timestampRange: {begin: string, end: string}}} baseAllowance
   */
  constructor(baseAllowance) {
    this.amount = baseAllowance.amount;
    this.amount_granted = baseAllowance.amountGranted;
    this.owner = EntityId.parse(baseAllowance.owner).toString();
    this.spender = EntityId.parse(baseAllowance.spender).toString();
    this.timestamp = {
      from: nsToSecNs(baseAllowance.timestampRange.begin),
      to: nsToSecNs(baseAllowance.timestampRange.end),
    };
  }
}

export default BaseAllowanceViewModel;
