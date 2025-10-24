// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static com.hedera.node.app.ids.schemas.V0590EntityIdSchema.ENTITY_COUNTS_STATE_ID;

import com.hedera.hapi.node.state.entity.EntityCounts;
import jakarta.inject.Named;

@Named
public class EntityCountsSingleton implements SingletonState<EntityCounts> {

    @Override
    public Integer getId() {
        return ENTITY_COUNTS_STATE_ID;
    }

    @Override
    public EntityCounts get() {
        return EntityCounts.DEFAULT;
    }
}
