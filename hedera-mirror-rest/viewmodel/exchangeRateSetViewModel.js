// SPDX-License-Identifier: Apache-2.0

import ExchangeRateViewModel from './exchangeRateViewModel.js';
import {nsToSecNs} from '../utils.js';

/**
 * Exchange rate set view model
 */
class ExchangeRateSetViewModel {
  static currentLabel = 'current_';
  static nextLabel = 'next_';

  /**
   * Constructs exchange rate set view model
   *
   * @param {ExchangeRateSet} exchangeRateSet
   */
  constructor(exchangeRateSet) {
    this.current_rate = new ExchangeRateViewModel(exchangeRateSet, ExchangeRateSetViewModel.currentLabel);
    this.next_rate = new ExchangeRateViewModel(exchangeRateSet, ExchangeRateSetViewModel.nextLabel);
    this.timestamp = nsToSecNs(exchangeRateSet.timestamp);
  }
}

export default ExchangeRateSetViewModel;
