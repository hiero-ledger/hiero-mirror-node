// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.singleton;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.web3.Web3IntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class EntityIdSingletonIntegrationTest extends Web3IntegrationTest {

    private final EntityIdSingleton entityIdSingleton;
    private final CommonProperties commonProperties;

    @Test
    void shouldReturnNextIdWithIncrementAndRealmAndShard() {
        // Create an entity with shard and realm set to (1,1)
        final var entityWithShardAndRealm =
                domainBuilder.entity().customize(e -> e.shard(1L).realm(1L)).persist();

        // Get ID before setting the correct shard and realm
        final var entityNumberBeforeConfig = entityIdSingleton.get();

        // Set correct shard and realm
        commonProperties.setRealm(1L);
        commonProperties.setShard(1L);
        final var entityNumberAfterConfig = entityIdSingleton.get();

        // Reset to default shard and realm (0,0)
        commonProperties.setRealm(0L);
        commonProperties.setShard(0L);

        final var entity2 = domainBuilder.entity().persist();
        final var entityNumber2 = entityIdSingleton.get();

        final var entity3 = domainBuilder.entity().persist();
        final var entityNumber3 = entityIdSingleton.get();

        assertThat(entityNumberBeforeConfig.number()).isNotEqualTo(entityWithShardAndRealm.getNum() + 1);

        assertThat(entityNumberAfterConfig.number()).isEqualTo(entityWithShardAndRealm.getNum() + 1);

        assertThat(entityNumber2.number()).isEqualTo(entity2.getNum() + 1);

        assertThat(entityNumber3.number()).isEqualTo(entity3.getNum() + 1);
    }
}
