// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor
class RecordFileRepositoryTest extends Web3IntegrationTest {

    private static final String HASH_64 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String HASH = HASH_64 + "0".repeat(32);

    @Resource
    private RecordFileRepository recordFileRepository;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Test
    void findEarliest() {
        final var genesisRecordFile =
                domainBuilder.recordFile().customize(f -> f.index(0L)).persist();
        domainBuilder.recordFile().persist();

        assertThat(recordFileRepository.findEarliest()).get().isEqualTo(genesisRecordFile);
    }

    @Test
    void findLatest() {
        domainBuilder.recordFile().persist();
        var latest = domainBuilder.recordFile().persist();

        assertThat(recordFileRepository.findLatest()).get().isEqualTo(latest);
    }

    @Test
    void findByIndex() {
        domainBuilder.recordFile().persist();
        var latest = domainBuilder.recordFile().persist();
        long blockNumber = latest.getIndex();

        assertThat(recordFileRepository.findByIndex(blockNumber))
                .map(RecordFile::getConsensusEnd)
                .hasValue(latest.getConsensusEnd());
    }

    @Test
    void findByIndexNotExists() {
        long nonExistentBlockNumber = 1L;
        assertThat(recordFileRepository.findByIndex(nonExistentBlockNumber)).isEmpty();
    }

    @Test
    void findByTimestamp() {
        var timestamp = domainBuilder.timestamp();
        var recordFile = domainBuilder
                .recordFile()
                .customize(r -> {
                    r.consensusStart(timestamp);
                    r.consensusEnd(timestamp + 1);
                })
                .persist();
        assertThat(recordFileRepository.findByTimestamp(timestamp)).contains(recordFile);
    }

    @Test
    void findByIndexServesFromCacheWithoutQueryingDatabase() {
        final var recordFile = domainBuilder.recordFile().persist();
        final long index = recordFile.getIndex();

        assertThat(recordFileRepository.findByIndex(index)).contains(recordFile);
        jdbcTemplate.update("delete from record_file");
        assertThat(recordFileRepository.findByIndex(index))
                .as("findByIndex should serve the cached record file without hitting the DB")
                .contains(recordFile);
    }

    @Test
    void findByTimestampServesFromCacheWithoutQueryingDatabase() {
        final var timestamp = domainBuilder.timestamp();
        final var recordFile = domainBuilder
                .recordFile()
                .customize(r -> {
                    r.consensusStart(timestamp);
                    r.consensusEnd(timestamp + 1);
                })
                .persist();

        assertThat(recordFileRepository.findByTimestamp(timestamp)).contains(recordFile);
        jdbcTemplate.update("delete from record_file");
        assertThat(recordFileRepository.findByTimestamp(timestamp))
                .as("findByTimestamp should serve the cached record file without hitting the DB")
                .contains(recordFile);
    }

    @CsvSource({
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef00000000000000000000000000000000",
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    })
    @ParameterizedTest
    void findByHash(final String value) {
        final var recordFile =
                domainBuilder.recordFile().customize(r -> r.hash(HASH)).persist();

        final var result = recordFileRepository.findByHash(value);
        assertThat(result.isPresent()).isTrue();
        assertThat(result).contains(recordFile);
    }
}
