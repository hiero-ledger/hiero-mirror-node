// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.balance;

import static org.assertj.core.api.Assertions.assertThat;

import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.reader.balance.line.AccountBalanceLineParserV2;
import org.junit.jupiter.api.Test;

class BalanceFileReaderImplV2Test extends CsvBalanceFileReaderTest {

    static final String FILE_PATH = getTestFilename("v2", "2020-09-22T04_25_00.083212003Z_Balances.csv");

    BalanceFileReaderImplV2Test() {
        super(BalanceFileReaderImplV2.class, AccountBalanceLineParserV2.class, FILE_PATH, 106L);
    }

    @Test
    void supportsInvalidWhenWrongVersion() {
        StreamFileData streamFileData =
                StreamFileData.from(balanceFile.getName(), BalanceFileReaderImplV1.TIMESTAMP_HEADER_PREFIX);
        assertThat(balanceFileReader.supports(streamFileData)).isFalse();
    }
}
