// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.ethereum;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
abstract class AbstractEthereumTransactionParserTest extends ImporterIntegrationTest {

    protected final EthereumTransactionParser ethereumTransactionParser;

    protected abstract byte[] getTransactionBytes();

    protected abstract void validateEthereumTransaction(EthereumTransaction ethereumTransaction);

    @Test
    void decode() {
        var ethereumTransaction = ethereumTransactionParser.decode(getTransactionBytes());
        validateEthereumTransaction(ethereumTransaction);
    }
}
