// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.balance;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.Strings;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.balance.AccountBalanceFile;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.exception.InvalidDatasetException;
import org.hiero.mirror.importer.parser.balance.BalanceParserProperties;
import org.hiero.mirror.importer.reader.balance.line.AccountBalanceLineParser;

@CustomLog
@RequiredArgsConstructor
public abstract class CsvBalanceFileReader implements BalanceFileReader {

    static final int BUFFER_SIZE = 16;
    static final Charset CHARSET = StandardCharsets.UTF_8;
    static final String COLUMN_HEADER_PREFIX = "shard";
    private static final String FILE_EXTENSION = "csv";

    private final BalanceParserProperties balanceParserProperties;
    private final AccountBalanceLineParser parser;

    @Override
    public boolean supports(StreamFileData streamFileData) {
        if (!FILE_EXTENSION.equals(
                streamFileData.getStreamFilename().getExtension().getName())) {
            return false;
        }

        InputStream inputStream = streamFileData.getInputStream();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, CHARSET), BUFFER_SIZE)) {
            String firstLine = reader.readLine();
            return firstLine != null && supports(firstLine);
        } catch (Exception e) {
            throw new InvalidDatasetException("Error reading account balance file", e);
        }
    }

    protected boolean supports(String firstLine) {
        return Strings.CI.startsWith(firstLine, getVersionHeaderPrefix());
    }

    protected abstract String getTimestampHeaderPrefix();

    protected abstract String getVersionHeaderPrefix();

    @Override
    public AccountBalanceFile read(StreamFileData streamFileData) {
        MessageDigest messageDigest = DigestUtils.getSha384Digest();
        int bufferSize = balanceParserProperties.getFileBufferSize();

        try (InputStream inputStream = new DigestInputStream(streamFileData.getInputStream(), messageDigest);
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, CHARSET), bufferSize)) {
            long consensusTimestamp = parseConsensusTimestamp(reader);
            AtomicLong count = new AtomicLong(0L);
            List<AccountBalance> items = new ArrayList<>();

            AccountBalanceFile accountBalanceFile = new AccountBalanceFile();
            accountBalanceFile.setBytes(streamFileData.getBytes());
            accountBalanceFile.setConsensusTimestamp(consensusTimestamp);
            accountBalanceFile.setLoadStart(streamFileData.getStreamFilename().getTimestamp());
            accountBalanceFile.setName(streamFileData.getFilename());

            reader.lines()
                    .map(line -> {
                        try {
                            AccountBalance accountBalance = parser.parse(line, consensusTimestamp);
                            count.incrementAndGet();
                            return accountBalance;
                        } catch (InvalidDatasetException ex) {
                            log.error("Error reading line", ex);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .forEachOrdered(items::add);

            accountBalanceFile.setCount(count.get());
            accountBalanceFile.setFileHash(DomainUtils.bytesToHex(messageDigest.digest()));
            accountBalanceFile.setItems(items);
            return accountBalanceFile;
        } catch (IOException ex) {
            throw new InvalidDatasetException("Error reading account balance file", ex);
        }
    }

    protected abstract long parseConsensusTimestamp(BufferedReader reader);

    protected long convertTimestamp(String timestamp) {
        Instant instant = Instant.parse(timestamp);
        return DomainUtils.convertToNanosMax(instant);
    }
}
