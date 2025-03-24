// SPDX-License-Identifier: Apache-2.0

import config, {getMirrorConfig} from '../config';
import EntityId from '../entityId';

const {
  common: {realm: systemRealm, shard: systemShard},
} = getMirrorConfig();

const getUnreleasedSupplyAccounts = () => {
  return config.network.unreleasedSupplyAccounts.map((range) => {
    const from = EntityId.of(systemShard, systemRealm, range.from);
    const to = EntityId.of(systemShard, systemRealm, range.to);
    return {from, to};
  });
};

class SystemEntity {
  #addressBookFile102Id = EntityId.of(systemShard, systemRealm, 102);
  #exchangeRateFileId = EntityId.of(systemShard, systemRealm, 112);
  #feeScheduleFileId = EntityId.of(systemShard, systemRealm, 111);
  #stakingRewardAccount = EntityId.of(systemShard, systemRealm, 800);
  #treasuryAccount = EntityId.of(systemShard, systemRealm, 2);
  #unreleasedSupplyAccounts = getUnreleasedSupplyAccounts();

  get stakingRewardAccount() {
    return this.#stakingRewardAccount;
  }

  get treasuryAccount() {
    return this.#treasuryAccount;
  }

  get exchangeRateFileId() {
    return this.#exchangeRateFileId;
  }

  get feeScheduleFileId() {
    return this.#feeScheduleFileId;
  }

  get addressBookFile102Id() {
    return this.#addressBookFile102Id;
  }

  get unreleasedSupplyAccounts() {
    return this.#unreleasedSupplyAccounts;
  }
}

export default new SystemEntity();
