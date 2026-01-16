// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static com.hedera.node.app.service.entityid.impl.schemas.V0590EntityIdSchema.ENTITY_COUNTS_STATE_ID;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hedera.hapi.node.state.entity.EntityCounts;
import org.junit.jupiter.api.Test;

class EntityCountsSingletonTest {

    private final EntityCountsSingleton entityCountsSingleton = new EntityCountsSingleton();

    @Test
    void testExpectedValueIsReturned() {
        assertThat(entityCountsSingleton.get()).isEqualTo(EntityCounts.DEFAULT);
    }

    @Test
    void testGetKeyReturnsExpectedKey() {
        assertThat(entityCountsSingleton.getStateId()).isEqualTo(ENTITY_COUNTS_STATE_ID);
    }
}
