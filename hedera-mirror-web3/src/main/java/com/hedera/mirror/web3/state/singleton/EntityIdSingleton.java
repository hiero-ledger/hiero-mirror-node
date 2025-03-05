// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.singleton;

import static com.hedera.node.app.ids.schemas.V0490EntityIdSchema.ENTITY_ID_STATE_KEY;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.repository.EntityRepository;
import com.hedera.node.config.data.HederaConfig;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
@SuppressWarnings("deprecation")
public class EntityIdSingleton implements SingletonState<EntityNumber> {
    private final EntityRepository entityRepository;
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;
    private final CommonProperties commonProperties;

    @Override
    public String getKey() {
        return ENTITY_ID_STATE_KEY;
    }

    @Override
    public EntityNumber get() {
        final long firstUserEntity = mirrorNodeEvmProperties
                .getVersionedConfiguration()
                .getConfigData(HederaConfig.class)
                .firstUserEntity();
        Long maxId = entityRepository.findMaxId(commonProperties.getShard(), commonProperties.getRealm());
        final var maxEntityId = EntityId.of(maxId != null ? maxId : firstUserEntity);
        final var nextId = Math.max(maxEntityId.getNum() + 1, firstUserEntity);
        return new EntityNumber(nextId);
    }
}
