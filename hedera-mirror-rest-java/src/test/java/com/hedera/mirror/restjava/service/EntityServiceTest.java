// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.restjava.RestJavaIntegrationTest;
import com.hedera.mirror.restjava.common.EntityIdAliasParameter;
import com.hedera.mirror.restjava.common.EntityIdEvmAddressParameter;
import com.hedera.mirror.restjava.common.EntityIdNumParameter;
import com.hedera.mirror.restjava.common.EntityIdParameter;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@RequiredArgsConstructor
class EntityServiceTest extends RestJavaIntegrationTest {

    private final EntityService service;

    @Test
    void findById() {
        var entity = domainBuilder.entity().persist();
        assertThat(service.findById(entity.toEntityId())).isEqualTo(entity);
    }

    @Test
    void findByIdInvalidShard() {
        var id = EntityId.of(1L, 2L, 3L);
        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ID " + id + " has an invalid shard. Shard must be 0");
    }

    @Test
    void findByIdNotFound() {
        var id = EntityId.of(3L);
        assertThatThrownBy(() -> service.findById(id)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void lookup() {
        var entity = domainBuilder.entity().persist();
        var id = entity.toEntityId();

        assertThat(service.lookup(new EntityIdNumParameter(entity.toEntityId())))
                .isEqualTo(id);
        assertThat(service.lookup(new EntityIdEvmAddressParameter(0, 0, entity.getEvmAddress())))
                .isEqualTo(id);
        assertThat(service.lookup(new EntityIdAliasParameter(0, 0, entity.getAlias())))
                .isEqualTo(id);

        // Valid numeric account IDs are not looked up in the entity table in support of partial mirror nodes.
        var unknownAccountId = EntityIdNumParameter.valueOf("0.0.5000");
        assertThat(service.lookup(unknownAccountId)).isEqualTo(unknownAccountId.id());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "0.0.000000000000000000000000000000000186Fb1b",
                "0.0.AABBCC22",
            })
    void lookupNotFound(String id) {
        var entityIdParameter = EntityIdParameter.valueOf(id);
        assertThatThrownBy(() -> service.lookup(entityIdParameter)).isInstanceOf(EntityNotFoundException.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "0.1.5000",
                "1.0.5000",
                "0.1.000000000000000000000000000000000186Fb1b",
                "1.0.000000000000000000000000000000000186Fb1b",
                "0.1.AABBCC22",
                "1.0.AABBCC22",
            })
    void lookupInvalidShardRealm(String id) {
        var entityIdParameter = EntityIdParameter.valueOf(id);
        assertThatThrownBy(() -> service.lookup(entityIdParameter)).isInstanceOf(IllegalArgumentException.class);
    }
}
