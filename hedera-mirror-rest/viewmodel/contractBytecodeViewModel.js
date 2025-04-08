// SPDX-License-Identifier: Apache-2.0

import * as utils from '../utils.js';
import ContractViewModel from './contractViewModel.js';

/**
 * Contract with bytecode view model
 */
class ContractBytecodeViewModel extends ContractViewModel {
  /**
   * Constructs contract view model
   *
   * @param {Contract} contract
   * @param {Entity} entity
   */
  constructor(contract, entity) {
    super(contract, entity);
    this.bytecode = utils.addHexPrefix(contract.bytecode);
    this.runtime_bytecode = utils.toHexString(contract.runtimeBytecode, true);
  }
}

export default ContractBytecodeViewModel;
