// SPDX-License-Identifier: Apache-2.0

const {common} = getMirrorConfig();

class SystemEntity {
  get treasuryAccount() {
    return EntityId.of(common.shard, common.realm, 2);
  }
}

export default new SystemEntity();
