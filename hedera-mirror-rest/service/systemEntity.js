// SPDX-License-Identifier: Apache-2.0

import {getMirrorConfig} from '../config';
import EntityId from '../entityId';

const {
  common: {realm: systemRealm, shard: systemShard},
} = getMirrorConfig();

class SystemEntity {
  #stakingRewardAccount = EntityId.of(systemShard, systemRealm, 800);
  #treasuryAccount = EntityId.of(systemShard, systemRealm, 2);

  get stakingRewardAccount() {
    return this.#stakingRewardAccount;
  }

  get treasuryAccount() {
    return this.#treasuryAccount;
  }
}

export default new SystemEntity();
