// SPDX-License-Identifier: Apache-2.0

import _ from 'lodash';
import {proto} from '@hashgraph/proto';

import {orderFilterValues} from '../constants.js';
import {convertGasPriceToTinyBars, nsToSecNs} from '../utils.js';

/**
 * Fee schedule view model
 */
class FeeScheduleViewModel {
  static currentLabel = 'current_';
  static nextLabel = 'next_';
  static enabledTxTypesMap = {
    [proto.HederaFunctionality.ContractCall]: 'ContractCall',
    [proto.HederaFunctionality.ContractCreate]: 'ContractCreate',
    [proto.HederaFunctionality.EthereumTransaction]: 'EthereumTransaction',
  };
  /**
   * Constructs fee schedule view model
   *
   * @param {FeeSchedule} feeSchedule
   * @param {ExchangeRate} exchangeRate
   * @param {'asc'|'desc'} order
   * @param {boolean} current Default value is true
   */
  constructor(feeSchedule, exchangeRate, order, current = true) {
    const prefix = current ? FeeScheduleViewModel.currentLabel : FeeScheduleViewModel.nextLabel;
    const schedule = feeSchedule[`${prefix}feeSchedule`];
    const hbarRate = exchangeRate[`${prefix}hbar`];
    const centRate = exchangeRate[`${prefix}cent`];

    this.fees = schedule
      .filter(({hederaFunctionality}) =>
        _.keys(FeeScheduleViewModel.enabledTxTypesMap).includes(hederaFunctionality.toString())
      )
      .map(({fees, hederaFunctionality}) => {
        const fee = _.first(fees);
        const gasPrice = _.result(fee, 'servicedata.gas.toNumber');
        const tinyBars = convertGasPriceToTinyBars(gasPrice, hbarRate, centRate);

        // make sure the gas price is converted successfully, otherwise something is wrong with gasPrice or exchange rate, so skip the current fee
        if (_.isNil(tinyBars)) {
          return null;
        }

        return {
          gas: tinyBars,
          transaction_type: FeeScheduleViewModel.enabledTxTypesMap[hederaFunctionality],
        };
      })
      .filter((f) => !_.isNil(f))
      .sort((curr, next) => {
        // localCompare by default sorts the array in ascending order, so when its multiplied by -1 the sort order is reversed
        const sortOrder = order.toLowerCase() === orderFilterValues.ASC ? 1 : -1;
        return curr.transaction_type.localeCompare(next.transaction_type) * sortOrder;
      });

    this.timestamp = nsToSecNs(feeSchedule.timestamp);
  }
}

export default FeeScheduleViewModel;
