// SPDX-License-Identifier: Apache-2.0

import {getMirrorConfig} from './config';
import EntityId from './entityId';

const {common} = getMirrorConfig();

class SystemEntity {
  get treasuryAccount() {
    return EntityId.of(common.shard, common.realm, 2);
  }
}

export default new SystemEntity();
