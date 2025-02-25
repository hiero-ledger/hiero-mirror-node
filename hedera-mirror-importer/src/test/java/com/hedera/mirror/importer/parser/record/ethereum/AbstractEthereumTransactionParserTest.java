// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.ethereum;

import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.RequiredArgsConstructor;
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
