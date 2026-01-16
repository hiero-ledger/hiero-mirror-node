// SPDX-License-Identifier: Apache-2.0

import EntityId from '../entityId';
import * as utils from '../utils';

/**
 * Contract view model
 */
class ContractViewModel {
  /**
   * Constructs contract view model
   *
   * @param {Contract} contract
   * @param {Entity} entity
   */
  constructor(contract, entity) {
    const contractId = EntityId.parse(entity.id);
    this.admin_key = utils.encodeKey(entity.key);
    this.auto_renew_account = EntityId.parse(entity.autoRenewAccountId, {isNullable: true}).toString();
    this.auto_renew_period = entity.autoRenewPeriod;
    this.contract_id = contractId.toString();
    this.created_timestamp = utils.nsToSecNs(entity.createdTimestamp);
    this.deleted = entity.deleted;
    this.evm_address =
      entity.evmAddress !== null ? utils.toHexString(entity.evmAddress, true) : contractId.toEvmAddress();
    this.expiration_timestamp = utils.nsToSecNs(
      utils.calculateExpiryTimestamp(entity.autoRenewPeriod, entity.createdTimestamp, entity.expirationTimestamp)
    );
    this.file_id = EntityId.parse(contract.fileId, {isNullable: true}).toString();
    this.max_automatic_token_associations = entity.maxAutomaticTokenAssociations;
    this.memo = entity.memo;
    this.nonce = entity.ethereumNonce;
    this.obtainer_id = EntityId.parse(entity.obtainerId, {isNullable: true}).toString();
    this.permanent_removal = entity.permanentRemoval;
    this.proxy_account_id = EntityId.parse(entity.proxyAccountId, {isNullable: true}).toString();
    this.timestamp = {
      from: utils.nsToSecNs(entity.timestampRange.begin),
      to: utils.nsToSecNs(entity.timestampRange.end),
    };
  }
}

export default ContractViewModel;
