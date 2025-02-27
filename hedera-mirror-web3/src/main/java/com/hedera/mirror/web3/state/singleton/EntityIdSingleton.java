// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.singleton;

import static com.hedera.node.app.ids.schemas.V0490EntityIdSchema.ENTITY_ID_STATE_KEY;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.mirror.web3.repository.EntityRepository;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
@SuppressWarnings("deprecation")
public class EntityIdSingleton implements SingletonState<EntityNumber> {

    public static final long FIRST_USER_ENTITY_ID = 1001;
    private final EntityRepository entityRepository;

    @Override
    public String getKey() {
        return ENTITY_ID_STATE_KEY;
    }

    @Override
    public EntityNumber get() {
        final Long maxId = entityRepository.findMaxId();
        final long nextId = (maxId != null && maxId >= FIRST_USER_ENTITY_ID) ? maxId + 1 : FIRST_USER_ENTITY_ID;
        return new EntityNumber(nextId);
    }
}
