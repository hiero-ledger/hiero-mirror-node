// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PersistPropertiesTest {

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
            , false
            0.0.0, false
            0.0.10, true
            0.0.98, false
            0.0.800, false
            """)
    void shouldPersistEntityTransaction(String entityIdStr, boolean expected) {
        var persistProperties = new EntityProperties.PersistProperties(new CommonProperties());
        persistProperties.setEntityTransactions(true);
        var entityId = entityIdStr != null ? EntityId.of(entityIdStr) : null;
        assertThat(persistProperties.shouldPersistEntityTransaction(entityId)).isEqualTo(expected);
    }

    @Test
    void shouldPersistEntityTransactionWhenDisabled() {
        var persistProperties = new EntityProperties.PersistProperties(new CommonProperties());
        persistProperties.setEntityTransactions(false);
        assertThat(persistProperties.shouldPersistEntityTransaction(null)).isFalse();
        assertThat(persistProperties.shouldPersistEntityTransaction(EntityId.EMPTY))
                .isFalse();
        assertThat(persistProperties.shouldPersistEntityTransaction(EntityId.of(10)))
                .isFalse();
        assertThat(persistProperties.shouldPersistEntityTransaction(EntityId.of(98)))
                .isFalse();
        assertThat(persistProperties.shouldPersistEntityTransaction(EntityId.of(800)))
                .isFalse();
    }

    @Test
    void shouldPersistEntityTransactionWithCustomExclusion() {
        var persistProperties = new EntityProperties.PersistProperties(new CommonProperties());
        persistProperties.setEntityTransactions(true);
        persistProperties.setEntityTransactionExclusion(Set.of(EntityId.of(10)));
        assertThat(persistProperties.shouldPersistEntityTransaction(null)).isFalse();
        assertThat(persistProperties.shouldPersistEntityTransaction(EntityId.EMPTY))
                .isFalse();
        assertThat(persistProperties.shouldPersistEntityTransaction(EntityId.of(10)))
                .isFalse();
        assertThat(persistProperties.shouldPersistEntityTransaction(EntityId.of(98)))
                .isTrue();
        assertThat(persistProperties.shouldPersistEntityTransaction(EntityId.of(800)))
                .isTrue();
    }

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
            true, CRYPTOTRANSFER, true,
            true, CONSENSUSSUBMITMESSAGE, false,
            false, CRYPTOTRANSFER, false,
            false, CONSENSUSSUBMITMESSAGE, false,
            """)
    void shouldPersistTransactionHash(boolean transactionHash, TransactionType transactionType, boolean expected) {
        var persistProperties = new EntityProperties.PersistProperties(new CommonProperties());
        persistProperties.setTransactionHash(transactionHash);
        persistProperties.setTransactionHashTypes(Set.of(transactionType));
        assertThat(persistProperties.shouldPersistTransactionHash(TransactionType.CRYPTOTRANSFER))
                .isEqualTo(expected);
        assertThat(persistProperties.shouldPersistTransactionHash(TransactionType.CONTRACTCALL))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldPersistTransactionHashWhenEmptyFilter(boolean transactionHash) {
        var persistProperties = new EntityProperties.PersistProperties(new CommonProperties());
        persistProperties.setTransactionHash(transactionHash);
        persistProperties.setTransactionHashTypes(Collections.emptySet());
        assertThat(persistProperties.shouldPersistTransactionHash(TransactionType.CRYPTOTRANSFER))
                .isEqualTo(transactionHash);
        assertThat(persistProperties.shouldPersistTransactionHash(TransactionType.CONTRACTCALL))
                .isEqualTo(transactionHash);
    }
}
