// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository.upsert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.token.DissociateTokenTransfer;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class UpsertQueryGeneratorFactoryTest extends ImporterIntegrationTest {

    private final DissociateTokenTransferUpsertQueryGenerator customUpsertQueryGenerator;
    private final UpsertQueryGeneratorFactory factory;

    @Test
    void getExistingGenerator() {
        assertThat(factory.get(DissociateTokenTransfer.class)).isEqualTo(customUpsertQueryGenerator);
    }

    @Test
    void getGenericGenerator() {
        assertThat(factory.get(Entity.class)).isInstanceOf(GenericUpsertQueryGenerator.class);
    }

    @Test
    void unsupportedClass() {
        assertThatThrownBy(() -> factory.get(Object.class))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("not annotated with @Upsertable");
    }
}
