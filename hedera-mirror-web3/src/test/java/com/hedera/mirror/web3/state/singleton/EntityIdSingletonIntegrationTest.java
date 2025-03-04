// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.singleton;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.web3.Web3IntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
public class EntityIdSingletonIntegrationTest extends Web3IntegrationTest {

    private final EntityIdSingleton entityIdSingleton;

    @Test
    void shouldReturnNextIdWithIncrementAndRealmAndShard() {
        final var entity1 = domainBuilder.entity().persist();
        final var entityNumber1 = entityIdSingleton.get();
        final var entity2 = domainBuilder.entity().persist();
        final var entityNumber2 = entityIdSingleton.get();
        final var entityShardAndRealm =
                domainBuilder.entity().customize(e -> e.shard(1L).realm(1L)).persist();
        final var entityNumber3 = entityIdSingleton.get();
        assertThat(entityNumber1.number()).isEqualTo(entity1.toEntityId().getNum() + 1);
        assertThat(entityNumber2.number()).isEqualTo(entity2.toEntityId().getNum() + 1);
        assertThat(entityNumber3.number())
                .isEqualTo(entityShardAndRealm.toEntityId().getNum() + 1);
    }
}
