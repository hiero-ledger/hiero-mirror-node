// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.balance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.hiero.mirror.common.domain.balance.AccountBalanceFile;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.reader.balance.line.AccountBalanceLineParserV1;
import org.junit.jupiter.api.Test;

class BalanceFileReaderImplV1Test extends CsvBalanceFileReaderTest {

    static final String FILE_PATH = getTestFilename("v1", "2019-08-30T18_15_00.016002001Z_Balances.csv");

    BalanceFileReaderImplV1Test() {
        super(BalanceFileReaderImplV1.class, AccountBalanceLineParserV1.class, FILE_PATH, 25391L);
    }

    @Test
    void supportsInvalidWhenWrongVersion() {
        StreamFileData streamFileData =
                StreamFileData.from(balanceFile.getName(), BalanceFileReaderImplV2.VERSION_HEADER);
        assertThat(balanceFileReader.supports(streamFileData)).isFalse();
    }

    @Test
    void readValidFileWithLeadingEmptyLine() throws IOException {
        List<String> lines = FileUtils.readLines(balanceFile, CsvBalanceFileReader.CHARSET);
        List<String> copy = new ArrayList<>();
        copy.add("");
        copy.addAll(lines);
        FileUtils.writeLines(testFile, copy);
        StreamFileData streamFileData = StreamFileData.from(testFile);
        AccountBalanceFile accountBalanceFile = balanceFileReader.read(streamFileData);
        assertAccountBalanceFile(accountBalanceFile);
        verifySuccess(testFile, accountBalanceFile, 2);
    }
}
