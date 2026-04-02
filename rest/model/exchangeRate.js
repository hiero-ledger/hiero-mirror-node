// SPDX-License-Identifier: Apache-2.0

import {fromBinary} from '@bufbuild/protobuf';
import {ExchangeRateSetSchema} from '../gen/services/exchange_rate_pb.js';
import {FileDecodeError} from '../errors';

class ExchangeRate {
  /**
   * Parses exchange rate into object
   * Currently from proto, eventually from exchange_rate table
   */
  constructor(exchangeRate) {
    let exchangeRateSet;

    try {
      exchangeRateSet = fromBinary(ExchangeRateSetSchema, new Uint8Array(exchangeRate.file_data));
    } catch (error) {
      throw new FileDecodeError(error.message);
    }

    this.current_cent = exchangeRateSet.currentRate.centEquiv;
    this.current_expiration = Number(exchangeRateSet.currentRate.expirationTime.seconds);
    this.current_hbar = exchangeRateSet.currentRate.hbarEquiv;
    this.next_cent = exchangeRateSet.nextRate.centEquiv;
    this.next_expiration = Number(exchangeRateSet.nextRate.expirationTime.seconds);
    this.next_hbar = exchangeRateSet.nextRate.hbarEquiv;
    this.timestamp = exchangeRate.consensus_timestamp;
  }
}

export default ExchangeRate;
