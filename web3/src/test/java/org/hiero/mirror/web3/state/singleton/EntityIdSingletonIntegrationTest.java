// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class EntityIdSingletonIntegrationTest extends Web3IntegrationTest {

    private final EntityIdSingleton entityIdSingleton;
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Test
    void shouldReturnNextIdWithIncrementAndRealmAndShard() {
        final var currentShard = commonProperties.getShard();
        final var currentRealm = commonProperties.getRealm();
        // Create an entity with shard and realm set to value one above the current values
        final var entityWithShardAndRealm = domainBuilder
                .entity()
                .customize(e -> e.shard(currentShard + 1).realm(currentRealm + 1))
                .persist();

        // Get ID before setting the correct shard and realm
        final var entityNumberBeforeConfig = entityIdSingleton.get();

        // Set correct shard and realm
        commonProperties.setRealm(currentRealm + 1);
        commonProperties.setShard(currentShard + 1);
        final var entityNumberAfterConfig = entityIdSingleton.get();

        // Reset to previous shard and realm
        commonProperties.setRealm(currentRealm);
        commonProperties.setShard(currentShard);

        ContractCallContext.get().setEntityNumber(null);
        final var entity2 = domainBuilder
                .entity()
                .customize(e -> e.shard(currentShard).realm(currentRealm))
                .persist();
        final var entityNumber2 = entityIdSingleton.get();

        ContractCallContext.get().setEntityNumber(null);
        final var entity3 = domainBuilder
                .entity()
                .customize(e -> e.shard(currentShard).realm(currentRealm))
                .persist();
        final var entityNumber3 = entityIdSingleton.get();

        assertThat(entityNumberBeforeConfig.number())
                .isNotEqualTo(entityWithShardAndRealm.getNum()
                        + mirrorNodeEvmProperties.getEntityIdReservationHeadroom()
                        + 1);
        assertThat(entityNumberAfterConfig.number())
                .isEqualTo(entityWithShardAndRealm.getNum()
                        + mirrorNodeEvmProperties.getEntityIdReservationHeadroom()
                        + 1);
        assertThat(entityNumber2.number())
                .isEqualTo(entity2.getNum() + mirrorNodeEvmProperties.getEntityIdReservationHeadroom() + 1);
        assertThat(entityNumber3.number())
                .isEqualTo(entity3.getNum() + mirrorNodeEvmProperties.getEntityIdReservationHeadroom() + 1);
    }
}
