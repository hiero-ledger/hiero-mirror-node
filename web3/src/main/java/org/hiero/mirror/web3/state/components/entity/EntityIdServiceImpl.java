// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.components.entity;

import com.hedera.node.app.service.entityid.EntityIdService;
import com.hedera.node.app.service.entityid.impl.schemas.V0490EntityIdSchema;
import com.hedera.node.app.service.entityid.impl.schemas.V0590EntityIdSchema;
import com.swirlds.state.lifecycle.SchemaRegistry;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class EntityIdServiceImpl extends EntityIdService {

    @Override
    public void registerSchemas(SchemaRegistry registry) {
        registry.register(new V0490EntityIdSchema());
        registry.register(new V0590EntityIdSchema());
    }
}
