// SPDX-License-Identifier: Apache-2.0

const {
  common: {realm: systemRealm, shard: systemShard},
} = getMirrorConfig();

class SystemEntity {
  #stakingRewardAccount = EntityId.of(systemShard, systemRealm, 800);

  get stakingRewardAccount() {
    return this.#stakingRewardAccount;
  }
}

export default new SystemEntity();
