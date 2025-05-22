// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.createSha384Digest;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.Collections2;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.balance.AccountBalanceFile;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.domain.StreamFilename;
import org.hiero.mirror.importer.exception.InvalidDatasetException;
import org.hiero.mirror.importer.parser.balance.BalanceParserProperties;
import org.hiero.mirror.importer.reader.balance.line.AccountBalanceLineParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.cglib.core.ReflectUtils;
import org.springframework.util.StringUtils;

abstract class CsvBalanceFileReaderTest {

    protected final BalanceParserProperties balanceParserProperties;
    protected final File balanceFile;
    protected final CsvBalanceFileReader balanceFileReader;
    protected final AccountBalanceLineParser parser;
    protected final long expectedCount;
    protected File testFile;
    protected long consensusTimestamp;
    protected CommonProperties commonProperties = CommonProperties.getInstance();

    @TempDir
    Path tempDir;

    CsvBalanceFileReaderTest(
            Class<? extends CsvBalanceFileReader> balanceFileReaderClass,
            Class<? extends AccountBalanceLineParser> accountBalanceLineParserClass,
            String balanceFilePath,
            long expectedCount) {
        balanceParserProperties = new BalanceParserProperties();
        balanceFile = TestUtils.getResource(balanceFilePath);
        parser = (AccountBalanceLineParser) ReflectUtils.newInstance(accountBalanceLineParserClass);
        balanceFileReader = (CsvBalanceFileReader) ReflectUtils.newInstance(
                balanceFileReaderClass,
                new Class<?>[] {BalanceParserProperties.class, accountBalanceLineParserClass},
                new Object[] {balanceParserProperties, parser});
        this.expectedCount = expectedCount;
    }

    protected static String getTestFilename(String version, String filename) {
        return Path.of("data", "accountBalances", version, "balance0.0.3", filename)
                .toString();
    }

    @BeforeEach
    void setup() throws IOException {
        Instant instant = StreamFilename.from(balanceFile.getName()).getInstant();
        consensusTimestamp = DomainUtils.convertToNanosMax(instant);
        testFile = tempDir.resolve(balanceFile.getName()).toFile();
        assertThat(testFile.createNewFile()).isTrue();
    }

    @Test
    void readValid() throws Exception {
        StreamFileData streamFileData = StreamFileData.from(balanceFile);
        AccountBalanceFile accountBalanceFile = balanceFileReader.read(streamFileData);
        assertAccountBalanceFile(accountBalanceFile);
        assertFileHash(balanceFile, accountBalanceFile);
        verifySuccess(balanceFile, accountBalanceFile, 2);
    }

    @Test
    void readInvalidWhenFileHasNoTimestampHeader() throws IOException {
        List<String> lines = FileUtils.readLines(balanceFile, CsvBalanceFileReader.CHARSET);
        lines.remove(0);
        FileUtils.writeLines(testFile, lines);
        StreamFileData streamFileData = StreamFileData.from(testFile);
        assertThrows(InvalidDatasetException.class, () -> balanceFileReader.read(streamFileData));
    }

    @Test
    void readInvalidWhenFileHasInvalidVersion() throws IOException {
        List<String> lines = FileUtils.readLines(balanceFile, CsvBalanceFileReader.CHARSET);
        lines.remove(0);
        List<String> copy = new ArrayList<>();
        copy.add("# 0.1.0");
        copy.addAll(lines);
        FileUtils.writeLines(testFile, copy);

        StreamFileData streamFileData = StreamFileData.from(testFile);
        assertThrows(InvalidDatasetException.class, () -> balanceFileReader.read(streamFileData));
    }

    @Test
    void readInvalidWhenFileHasNoHeader() throws IOException {
        List<String> lines = FileUtils.readLines(balanceFile, CsvBalanceFileReader.CHARSET);
        lines.remove(0);
        lines.remove(0);
        FileUtils.writeLines(testFile, lines);

        StreamFileData streamFileData = StreamFileData.from(testFile);
        assertThrows(InvalidDatasetException.class, () -> balanceFileReader.read(streamFileData));
    }

    @Test
    void readInvalidWhenFileHasNoColumnHeader() throws IOException {
        Collection<String> lines = FileUtils.readLines(balanceFile, CsvBalanceFileReader.CHARSET);
        Collection<String> filtered =
                Collections2.filter(lines, line -> !line.contains(CsvBalanceFileReader.COLUMN_HEADER_PREFIX));
        FileUtils.writeLines(testFile, filtered);

        StreamFileData streamFileData = StreamFileData.from(testFile);
        assertThrows(InvalidDatasetException.class, () -> balanceFileReader.read(streamFileData));
    }

    @Test
    void readInvalidWhenFileIsEmpty() {
        StreamFileData streamFileData = StreamFileData.from(balanceFile.getName(), "");
        assertThrows(InvalidDatasetException.class, () -> balanceFileReader.read(streamFileData));
    }

    @Test
    void readInvalidWhenFileDoesNotExist() {
        StreamFileData streamFileData = StreamFileData.from(testFile);
        assertThrows(InvalidDatasetException.class, () -> balanceFileReader.read(streamFileData));
    }

    @Test
    void readInvalidWhenFileHasMalformedTimestamp() throws IOException {
        String prefix = balanceFileReader.getTimestampHeaderPrefix();
        Collection<String> lines = FileUtils.readLines(balanceFile, CsvBalanceFileReader.CHARSET);
        Collection<String> filtered =
                Collections2.transform(lines, line -> StringUtils.startsWithIgnoreCase(line, prefix) ? prefix : line);
        FileUtils.writeLines(testFile, filtered);

        StreamFileData streamFileData = StreamFileData.from(testFile);
        assertThrows(InvalidDatasetException.class, () -> balanceFileReader.read(streamFileData));
    }

    @Test
    void readValidWhenFileHasTrailingEmptyLines() throws IOException {
        List<String> lines = FileUtils.readLines(balanceFile, CsvBalanceFileReader.CHARSET);
        FileUtils.writeLines(testFile, lines);
        FileUtils.writeStringToFile(testFile, "\n\n\n", CsvBalanceFileReader.CHARSET, true);

        StreamFileData streamFileData = StreamFileData.from(testFile);
        AccountBalanceFile accountBalanceFile = balanceFileReader.read(streamFileData);
        assertAccountBalanceFile(accountBalanceFile);
        verifySuccess(testFile, accountBalanceFile, 2);
    }

    @Test
    void readValidWhenFileHasBadTrailingLines() throws IOException {
        List<String> lines = FileUtils.readLines(balanceFile, CsvBalanceFileReader.CHARSET);
        FileUtils.writeLines(testFile, lines);
        FileUtils.writeStringToFile(testFile, "\n0.0.3.20340\nfoobar\n", CsvBalanceFileReader.CHARSET, true);

        StreamFileData streamFileData = StreamFileData.from(testFile);
        AccountBalanceFile accountBalanceFile = balanceFileReader.read(streamFileData);
        assertAccountBalanceFile(accountBalanceFile);
        verifySuccess(testFile, accountBalanceFile, 2);
    }

    @Test
    void readValidWhenFileHasLinesWithDifferentShardNum() throws IOException {
        List<String> lines = FileUtils.readLines(balanceFile, CsvBalanceFileReader.CHARSET);
        FileUtils.writeLines(testFile, lines);
        long otherShard = commonProperties.getShard() + 1;
        FileUtils.writeStringToFile(
                testFile,
                String.format("%n%d,0,3,340%n%d,0,4,340%n", otherShard, otherShard),
                CsvBalanceFileReader.CHARSET,
                true);

        StreamFileData streamFileData = StreamFileData.from(testFile);
        AccountBalanceFile accountBalanceFile = balanceFileReader.read(streamFileData);
        assertAccountBalanceFile(accountBalanceFile);
        verifySuccess(testFile, accountBalanceFile, 2);
    }

    @Test
    void supports() {
        StreamFileData streamFileData = StreamFileData.from(balanceFile);
        assertThat(balanceFileReader.supports(streamFileData)).isTrue();
    }

    @Test
    void supportsInvalidWhenWrongExtension() {
        StreamFileData streamFileData = StreamFileData.from("2021-03-10T16:00:00Z_Balances.csv", "");
        assertThat(balanceFileReader.supports(streamFileData)).isFalse();
    }

    @Test
    void supportsInvalidWhenEmpty() {
        StreamFileData streamFileData = StreamFileData.from(balanceFile.getName(), "");
        assertThat(balanceFileReader.supports(streamFileData)).isFalse();
    }

    @Test
    void supportsInvalidWhenEmptyFirstLine() {
        String versionPrefix = balanceFileReader.getVersionHeaderPrefix();
        StreamFileData streamFileData = StreamFileData.from(balanceFile.getName(), '\n' + versionPrefix);
        assertThat(balanceFileReader.supports(streamFileData)).isFalse();
    }

    @Test
    void supportsInvalidWhenExceedsLineSize() {
        String versionPrefix = balanceFileReader.getVersionHeaderPrefix();
        String prefix = " ".repeat(CsvBalanceFileReader.BUFFER_SIZE + 1);
        StreamFileData streamFileData = StreamFileData.from(balanceFile.getName(), prefix + versionPrefix);
        assertThat(balanceFileReader.supports(streamFileData)).isFalse();
    }

    @Test
    void supportsInvalidWhenInvalidFirstLine() {
        StreamFileData streamFileData = StreamFileData.from(balanceFile.getName(), "junk");
        assertThat(balanceFileReader.supports(streamFileData)).isFalse();
    }

    protected void assertAccountBalanceFile(AccountBalanceFile accountBalanceFile) {
        assertThat(accountBalanceFile).isNotNull();
        assertThat(accountBalanceFile.getBytes()).isNotEmpty();
        assertThat(accountBalanceFile.getCount()).isEqualTo(expectedCount);
        assertThat(accountBalanceFile.getConsensusTimestamp()).isEqualTo(consensusTimestamp);
        assertThat(accountBalanceFile.getLoadStart()).isNotNull().isPositive();
        assertThat(accountBalanceFile.getName()).isEqualTo(balanceFile.getName());
    }

    protected void assertFileHash(File file, AccountBalanceFile accountBalanceFile) throws Exception {
        MessageDigest md = createSha384Digest();
        byte[] array = Files.readAllBytes(file.toPath());
        String fileHash = DomainUtils.bytesToHex(md.digest(array));
        assertThat(accountBalanceFile.getFileHash()).isEqualTo(fileHash);
    }

    protected void verifySuccess(File file, AccountBalanceFile accountBalanceFile, int skipLines) throws IOException {
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(new FileInputStream(file), CsvBalanceFileReader.CHARSET))) {

            while (skipLines > 0) {
                reader.readLine();
                skipLines--;
            }

            var accountBalances = accountBalanceFile.getItems();
            var lineIter = reader.lines().iterator();
            var accountBalanceIter = accountBalances.iterator();

            while (lineIter.hasNext()) {
                String line = lineIter.next();
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                try {
                    AccountBalance expectedItem = parser.parse(line, consensusTimestamp);
                    AccountBalance actualItem = accountBalanceIter.next();
                    assertThat(actualItem).isEqualTo(expectedItem);
                } catch (InvalidDatasetException ex) {
                }
            }

            assertThat(accountBalanceIter.hasNext()).isFalse();
        }
    }
}
