// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static com.hedera.node.app.service.entityid.impl.schemas.V0590EntityIdSchema.ENTITY_COUNTS_STATE_ID;

import com.hedera.hapi.node.state.entity.EntityCounts;
import com.hedera.node.app.service.entityid.EntityIdService;
import jakarta.inject.Named;
import org.hiero.mirror.web3.state.RegisterableState;

@Named
final class EntityCountsSingleton implements SingletonState<EntityCounts>, RegisterableState {

    @Override
    public Integer getId() {
        return ENTITY_COUNTS_STATE_ID;
    }

    @Override
    public String getServiceName() {
        return EntityIdService.NAME;
    }

    @Override
    public EntityCounts get() {
        return EntityCounts.DEFAULT;
    }
}
