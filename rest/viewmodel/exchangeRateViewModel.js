// SPDX-License-Identifier: Apache-2.0

/**
 * Exchange rate view model
 */
class ExchangeRateViewModel {
  /**
   * Constructs exchange rate view model
   *
   * @param {ExchangeRate} exchangeRate
   * @param {string} prefix
   */
  constructor(exchangeRate, prefix) {
    this.cent_equivalent = exchangeRate[`${prefix}cent`];
    this.expiration_time = exchangeRate[`${prefix}expiration`];
    this.hbar_equivalent = exchangeRate[`${prefix}hbar`];
  }
}

export default ExchangeRateViewModel;
