// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static com.hedera.node.app.ids.schemas.V0490EntityIdSchema.ENTITY_ID_STATE_KEY;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.node.config.data.HederaConfig;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.repository.EntityRepository;

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

        final Long maxId = entityRepository.findMaxId(commonProperties.getShard(), commonProperties.getRealm());

        if (maxId == null) {
            return new EntityNumber(EntityId.of(firstUserEntity).getNum());
        }

        final var maxEntityId = EntityId.of(maxId);
        final var nextId = Math.max(maxEntityId.getNum() + 1, firstUserEntity);
        return new EntityNumber(nextId);
    }
}
