// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.config;

import static org.apache.commons.lang3.ObjectUtils.max;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.common.util.DomainUtils.convertToNanosMax;
import static org.hiero.mirror.importer.TestUtils.plus;
import static org.hiero.mirror.importer.config.DateRangeCalculator.DateRangeFilter;
import static org.hiero.mirror.importer.config.DateRangeCalculator.STARTUP_TIME;
import static org.hiero.mirror.importer.domain.StreamFilename.FileType.DATA;
import static org.mockito.Mockito.doReturn;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hiero.mirror.common.domain.StreamFile;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.domain.StreamFilename;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.downloader.DownloaderProperties;
import org.hiero.mirror.importer.downloader.balance.BalanceDownloaderProperties;
import org.hiero.mirror.importer.downloader.block.BlockProperties;
import org.hiero.mirror.importer.downloader.record.RecordDownloaderProperties;
import org.hiero.mirror.importer.exception.InvalidConfigurationException;
import org.hiero.mirror.importer.repository.AccountBalanceFileRepository;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.hiero.mirror.importer.repository.StreamFileRepository;
import org.hiero.mirror.importer.util.Utility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class DateRangeCalculatorTest {

    private final Map<StreamType, StreamFileRepository<?, ?>> streamFileRepositories = new EnumMap<>(StreamType.class);

    @Mock
    private AccountBalanceFileRepository accountBalanceFileRepository;

    private BlockProperties blockProperties;
    private ImporterProperties importerProperties;
    private List<DownloaderProperties> downloaderPropertiesList;
    private DateRangeCalculator dateRangeCalculator;

    @Mock
    private RecordFileRepository recordFileRepository;

    @BeforeEach
    void setUp() {
        importerProperties = new ImporterProperties();
        importerProperties.setNetwork(ImporterProperties.HederaNetwork.TESTNET);
        blockProperties = new BlockProperties(importerProperties);
        var commonDownloaderProperties = new CommonDownloaderProperties(importerProperties);
        var balanceDownloaderProperties = new BalanceDownloaderProperties(commonDownloaderProperties);
        var recordDownloaderProperties = new RecordDownloaderProperties(commonDownloaderProperties);
        downloaderPropertiesList = List.of(balanceDownloaderProperties, recordDownloaderProperties);
        dateRangeCalculator = new DateRangeCalculator(
                accountBalanceFileRepository, blockProperties, importerProperties, recordFileRepository);

        balanceDownloaderProperties.setEnabled(true);
        recordDownloaderProperties.setEnabled(true);

        streamFileRepositories.putIfAbsent(StreamType.BALANCE, accountBalanceFileRepository);
        streamFileRepositories.putIfAbsent(StreamType.RECORD, recordFileRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void notSetAndDatabaseEmpty(final boolean blockstream) {
        blockProperties.setEnabled(blockstream);
        final var expectedDate = blockstream ? Instant.EPOCH : STARTUP_TIME;
        final var expectedFilter = new DateRangeFilter(expectedDate, null);
        for (var downloaderProperties : downloaderPropertiesList) {
            var streamType = downloaderProperties.getStreamType();
            assertThat(dateRangeCalculator.getLastStreamFile(streamType))
                    .isEqualTo(streamFile(streamType, expectedDate, true));
            assertThat(dateRangeCalculator.getFilter(streamType)).isEqualTo(expectedFilter);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void notSetAndDatabaseNotEmpty(final boolean blockstream) {
        blockProperties.setEnabled(blockstream);
        var past = STARTUP_TIME.minusSeconds(100);
        streamFileRepositories.forEach((streamType, repository) ->
                doReturn(streamFile(streamType, past, false)).when(repository).findLatest());
        verifyWhenLastStreamFileFromDatabase(past);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void startDateNotSetAndEndDateAfterLongMaxAndDatabaseNotEmpty(final boolean blockstream) {
        blockProperties.setEnabled(blockstream);
        var past = STARTUP_TIME.minusSeconds(100);
        importerProperties.setEndDate(Utility.MAX_INSTANT_LONG.plusNanos(1));
        streamFileRepositories.forEach((streamType, repository) ->
                doReturn(streamFile(streamType, past, false)).when(repository).findLatest());
        verifyWhenLastStreamFileFromDatabase(past);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void startDateSetAndDatabaseEmpty(final boolean blockstream) {
        blockProperties.setEnabled(blockstream);
        var startDate = STARTUP_TIME.plusSeconds(10L);
        importerProperties.setStartDate(startDate);
        var expectedFilter = new DateRangeFilter(importerProperties.getStartDate(), null);
        var expectedDate = importerProperties.getStartDate();
        for (var downloaderProperties : downloaderPropertiesList) {
            StreamType streamType = downloaderProperties.getStreamType();
            assertThat(dateRangeCalculator.getLastStreamFile(streamType))
                    .isEqualTo(streamFile(streamType, expectedDate, true));
            assertThat(dateRangeCalculator.getFilter(streamType)).isEqualTo(expectedFilter);
        }
    }

    @ParameterizedTest(name = "blockstream({0}) startDate {1}ns before application status, endDate")
    @CsvSource(value = {"true,0", "true,1", "false,0", "false,1"})
    void startDateNotAfterDatabase(final boolean blockstream, final long nanos) {
        blockProperties.setEnabled(blockstream);
        var past = STARTUP_TIME.minusSeconds(100);
        importerProperties.setStartDate(past.minusNanos(nanos));
        streamFileRepositories.forEach((streamType, repository) ->
                doReturn(streamFile(streamType, past, false)).when(repository).findLatest());
        verifyWhenLastStreamFileFromDatabase(past);
    }

    @ParameterizedTest(name = "blockstream({0}) startDate is {1}ns after application status")
    @CsvSource(textBlock = """
            true, 1
            true, 2_000_000_000
            true, 200_000_000_000
            false, 1
            false, 2_000_000_000
            false, 200_000_000_000
            """)
    void startDateAfterDatabase(final boolean blockstream, final long diffNanos) {
        blockProperties.setEnabled(blockstream);
        var lastFileInstant = Instant.now().minusSeconds(200);
        streamFileRepositories.forEach(
                (streamType, repository) -> doReturn(streamFile(streamType, lastFileInstant, false))
                        .when(repository)
                        .findLatest());

        var startDate = lastFileInstant.plusNanos(diffNanos);
        importerProperties.setStartDate(startDate);
        var effectiveStartDate = max(startDate, lastFileInstant);

        var expectedFilter = new DateRangeFilter(startDate, null);
        for (var downloaderProperties : downloaderPropertiesList) {
            var streamType = downloaderProperties.getStreamType();

            assertThat(dateRangeCalculator.getLastStreamFile(streamType))
                    .isEqualTo(streamFile(streamType, effectiveStartDate, true));
            assertThat(dateRangeCalculator.getFilter(downloaderProperties.getStreamType()))
                    .isEqualTo(expectedFilter);
        }
    }

    @ParameterizedTest(
            name = "startDate {0} endDate {1} database {2} violates (effective) start date <= " + "end date constraint")
    @CsvSource(
            value = {
                "2020-08-18T09:00:05.124Z, 2020-08-18T09:00:05.123Z,",
                "2020-08-18T09:00:04.123Z, 2020-08-18T09:00:05.123Z, 2020-08-18T09:00:05.124Z",
                "2020-08-18T09:00:04.123Z, 2020-08-18T09:00:05.123Z, 2020-08-18T09:00:06.123Z",
                ", 2020-08-18T09:00:05.123Z, 2020-08-19T09:00:05.111Z",
                ", 2020-08-18T09:00:05.123Z,"
            })
    void startDateNotBeforeEndDate(Instant startDate, Instant endDate, Instant lastFileDate) {
        importerProperties.setStartDate(startDate);
        importerProperties.setEndDate(endDate);

        if (lastFileDate != null) {
            streamFileRepositories.forEach(
                    (streamType, repository) -> doReturn(streamFile(streamType, lastFileDate, false))
                            .when(repository)
                            .findLatest());
        }

        for (var downloaderProperties : downloaderPropertiesList) {
            var streamType = downloaderProperties.getStreamType();
            assertThatThrownBy(() -> dateRangeCalculator.getLastStreamFile(streamType))
                    .isInstanceOf(InvalidConfigurationException.class);
        }
    }

    @ParameterizedTest(name = "timestamp {0} does not pass empty filter")
    @ValueSource(longs = {-10L, 0L, 1L, 10L, 8L, 100L})
    void emptyFilter(long timestamp) {
        DateRangeFilter filter = DateRangeFilter.empty();
        assertThat(filter.filter(timestamp)).isFalse();
    }

    @ParameterizedTest(name = "filter [{0}, {1}], timestamp {2}, pass: {3}")
    @CsvSource(
            value = {
                "1, 1, 1, true",
                "1, 10, 1, true",
                "1, 10, 10, true",
                "1, 10, 6, true",
                "1, 10, 0, false",
                "1, 10, 11, false",
                "1, 10, -1, false",
            })
    void filter(long start, long end, long timestamp, boolean expected) {
        var filter = new DateRangeFilter(Instant.ofEpochSecond(0, start), Instant.ofEpochSecond(0, end));
        assertThat(filter.filter(timestamp)).isEqualTo(expected);
    }

    private Optional<StreamFile<?>> streamFile(StreamType streamType, Instant instant, boolean nullConsensusEnd) {
        var streamFile = streamType.newStreamFile();
        long consensusStart = convertToNanosMax(instant);
        streamFile.setConsensusStart(consensusStart);
        if (!nullConsensusEnd) {
            streamFile.setConsensusEnd(plus(consensusStart, streamType.getFileCloseInterval()));
        }
        streamFile.setName(StreamFilename.getFilename(streamType, DATA, instant));
        return Optional.of(streamFile);
    }

    private void verifyWhenLastStreamFileFromDatabase(Instant fileInstant) {
        long expectedConsensusStart = convertToNanosMax(fileInstant);
        var expectedDateRangeFilter = new DateRangeFilter(fileInstant, null);
        for (var downloaderProperties : downloaderPropertiesList) {
            var streamType = downloaderProperties.getStreamType();
            long expectedConsensusEnd = streamType != StreamType.BALANCE
                    ? plus(expectedConsensusStart, streamType.getFileCloseInterval())
                    : expectedConsensusStart;
            assertThat(dateRangeCalculator.getLastStreamFile(streamType))
                    .get()
                    .returns(expectedConsensusStart, StreamFile::getConsensusStart)
                    .returns(expectedConsensusEnd, StreamFile::getConsensusEnd);
            assertThat(dateRangeCalculator.getFilter(streamType)).isEqualTo(expectedDateRangeFilter);
        }
    }
}
