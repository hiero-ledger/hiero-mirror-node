// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static com.hedera.node.app.service.entityid.impl.schemas.V0490EntityIdSchema.ENTITY_ID_STATE_ID;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.node.config.data.HederaConfig;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.repository.EntityRepository;

@Named
@RequiredArgsConstructor
@SuppressWarnings("deprecation")
public class EntityIdSingleton implements SingletonState<EntityNumber> {
    private final EntityRepository entityRepository;
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Override
    public Integer getId() {
        return ENTITY_ID_STATE_ID;
    }

    @Override
    public EntityNumber get() {
        final var context = ContractCallContext.get();
        final var cachedNumber = context.getEntityNumber();
        if (cachedNumber != null) {
            return cachedNumber;
        }
        final long firstUserEntity = mirrorNodeEvmProperties
                .getVersionedConfiguration()
                .getConfigData(HederaConfig.class)
                .firstUserEntity();

        final Long maxId = entityRepository.findMaxId();

        if (maxId == null) {
            return new EntityNumber(EntityId.of(firstUserEntity).getNum());
        }

        final long entityIdReservationHeadroom = mirrorNodeEvmProperties.getEntityNumBuffer();
        final var maxEntityId = EntityId.of(maxId);
        final var nextId = Math.max(maxEntityId.getNum() + entityIdReservationHeadroom + 1, firstUserEntity);
        final var entityNumber = new EntityNumber(nextId);
        context.setEntityNumber(entityNumber);
        return entityNumber;
    }
}
