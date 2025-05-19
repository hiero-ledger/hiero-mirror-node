// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@RequiredArgsConstructor
class TransactionHandlerFactoryTest extends ImporterIntegrationTest {

    private final TransactionHandlerFactory transactionHandlerFactory;

    @EnumSource(TransactionType.class)
    @ParameterizedTest
    void get(TransactionType transactionType) {
        assertThat(transactionHandlerFactory.get(transactionType))
                .isNotNull()
                .extracting(TransactionHandler::getType)
                .isEqualTo(transactionType);
    }
}
