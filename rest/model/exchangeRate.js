// SPDX-License-Identifier: Apache-2.0

import {proto} from '@hashgraph/proto';
import {FileDecodeError} from '../errors';

class ExchangeRate {
  /**
   * Parses exchange rate into object
   * Currently from proto, eventually from exchange_rate table
   */
  constructor(exchangeRate) {
    let exchangeRateSet = {};

    try {
      exchangeRateSet = proto.ExchangeRateSet.decode(exchangeRate.file_data);
    } catch (error) {
      throw new FileDecodeError(error.message);
    }

    this.current_cent = exchangeRateSet.currentRate.centEquiv;
    this.current_expiration = exchangeRateSet.currentRate.expirationTime.seconds.toNumber();
    this.current_hbar = exchangeRateSet.currentRate.hbarEquiv;
    this.next_cent = exchangeRateSet.nextRate.centEquiv;
    this.next_expiration = exchangeRateSet.nextRate.expirationTime.seconds.toNumber();
    this.next_hbar = exchangeRateSet.nextRate.hbarEquiv;
    this.timestamp = exchangeRate.consensus_timestamp;
  }
}

export default ExchangeRate;
