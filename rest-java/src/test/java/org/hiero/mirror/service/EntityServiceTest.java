// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.RestJavaIntegrationTest;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.restjava.parameter.EntityIdAliasParameter;
import org.hiero.mirror.restjava.parameter.EntityIdEvmAddressParameter;
import org.hiero.mirror.restjava.parameter.EntityIdNumParameter;
import org.hiero.mirror.restjava.parameter.EntityIdParameter;
import org.hiero.mirror.restjava.service.EntityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@RequiredArgsConstructor
final class EntityServiceTest extends RestJavaIntegrationTest {

    private final EntityService service;

    @Test
    void findById() {
        var entity = domainBuilder.entity().persist();
        assertThat(service.findById(EntityId.of(entity.getId()))).isEqualTo(entity);
    }

    @Test
    void findByIdNotFound() {
        var id = EntityId.of(3L);
        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Entity not found");
    }

    @Test
    void lookup() {
        var entity = domainBuilder.entity().persist();
        var id = EntityId.of(entity.getId());

        assertThat(service.lookup(new EntityIdNumParameter(EntityId.of(entity.getId()))))
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
                "0.0.HIQQEXWKW53RKN4W6XXC4Q232SYNZ3SZANVZZSUME5B5PRGXL663UAQA",
            })
    void lookupNotFound(String id) {
        var entityIdParameter = EntityIdParameter.valueOf(id);
        assertThatThrownBy(() -> service.lookup(entityIdParameter))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("No account found for the given ID");
    }
}
