// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Set;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PersistPropertiesTest {
    private static final SystemEntity SYSTEM_ENTITY = new SystemEntity(CommonProperties.getInstance());

    @ParameterizedTest
    @CsvSource(textBlock = """
            , false
            0, false
            10, true
            98, false
            800, false
            """)
    void shouldPersistEntityTransaction(Long entityNum, boolean expected) {
        var persistProperties = new EntityProperties.PersistProperties(SYSTEM_ENTITY);
        persistProperties.setEntityTransactions(true);
        assertThat(persistProperties.shouldPersistEntityTransaction(entityId(entityNum)))
                .isEqualTo(expected);
    }

    @Test
    void shouldPersistEntityTransactionWhenDisabled() {
        var persistProperties = new EntityProperties.PersistProperties(SYSTEM_ENTITY);
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
        var persistProperties = new EntityProperties.PersistProperties(SYSTEM_ENTITY);
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
    @CsvSource(textBlock = """
            true, CRYPTOTRANSFER, true,
            true, CONSENSUSSUBMITMESSAGE, false,
            false, CRYPTOTRANSFER, false,
            false, CONSENSUSSUBMITMESSAGE, false,
            """)
    void shouldPersistTransactionHash(boolean transactionHash, TransactionType transactionType, boolean expected) {
        var persistProperties = new EntityProperties.PersistProperties(SYSTEM_ENTITY);
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
        var persistProperties = new EntityProperties.PersistProperties(SYSTEM_ENTITY);
        persistProperties.setTransactionHash(transactionHash);
        persistProperties.setTransactionHashTypes(Collections.emptySet());
        assertThat(persistProperties.shouldPersistTransactionHash(TransactionType.CRYPTOTRANSFER))
                .isEqualTo(transactionHash);
        assertThat(persistProperties.shouldPersistTransactionHash(TransactionType.CONTRACTCALL))
                .isEqualTo(transactionHash);
    }

    private EntityId entityId(Long num) {
        if (num == null || num == 0) {
            return null;
        }

        var commonProperties = CommonProperties.getInstance();
        return EntityId.of(commonProperties.getShard(), commonProperties.getRealm(), num);
    }
}
