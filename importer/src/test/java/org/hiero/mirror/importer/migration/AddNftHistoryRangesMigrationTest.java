// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import java.io.File;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.hiero.mirror.common.domain.token.NftHistory;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.EnabledIfV1;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.TestPropertySource;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@EnabledIfV1
@RequiredArgsConstructor
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.87.2")
class AddNftHistoryRangesMigrationTest extends ImporterIntegrationTest {

    private static final RowMapper<NftHistory> NFT_HISTORY_ROW_MAPPER = rowMapper(NftHistory.class);

    private final JdbcOperations jdbcOperations;

    @Value("classpath:db/migration/v1/V1.88.0__add_nft_history_timestamp_ranges.sql")
    private File migrationSql;

    @Test
    void migrateEmpty() {
        runMigration();
        assertThat(findAllNftHistories()).isEmpty();
    }

    @Test
    void migrate() {
        // given
        var expectedNftHistories = new ArrayList<NftHistory>();
        var tokenId = 100L;
        var serialNumber = 10L;
        expectedNftHistories.add(domainBuilder
                .nftHistory()
                .customize(n -> n.tokenId(tokenId)
                        .serialNumber(serialNumber)
                        .timestampRange(Range.closedOpen(1683843345425999227L, 1683843345425999228L)))
                .persist());
        expectedNftHistories.add(domainBuilder
                .nftHistory()
                .customize(n -> n.tokenId(tokenId)
                        .serialNumber(serialNumber)
                        .timestampRange(Range.closedOpen(1683843345425999228L, 1685285944321751033L)))
                .persist());
        // This history entry's timestamp_range doesn't match the previous entry's timestamp_range
        var nftHistory3 = domainBuilder
                .nftHistory()
                .customize(n -> n.tokenId(tokenId)
                        .serialNumber(serialNumber)
                        .timestampRange(Range.closedOpen(1688402586436126488L, 1689891172344878003L)))
                .persist();
        expectedNftHistories.add(nftHistory3);

        // The migration will create this history entry to connect the two timestamp_ranges
        var splicedHistory1 = domainBuilder
                .nftHistory()
                .customize(n -> n.accountId(nftHistory3.getAccountId())
                        .createdTimestamp(nftHistory3.getCreatedTimestamp())
                        .metadata(nftHistory3.getMetadata())
                        .serialNumber(serialNumber)
                        .timestampRange(Range.closedOpen(1685285944321751033L, 1688402586436126488L))
                        .tokenId(tokenId))
                .get();
        expectedNftHistories.add(splicedHistory1);

        var tokenId2 = 200L;
        expectedNftHistories.add(domainBuilder
                .nftHistory()
                .customize(n -> n.tokenId(tokenId2).serialNumber(serialNumber).timestampRange(Range.closedOpen(1L, 2L)))
                .persist());
        expectedNftHistories.add(domainBuilder
                .nftHistory()
                .customize(n -> n.tokenId(tokenId2).serialNumber(serialNumber).timestampRange(Range.closedOpen(2L, 3L)))
                .persist());
        // This history's lower timestamp range doesn't match the upper timestamp range of the previous history
        var token2History3 = domainBuilder
                .nftHistory()
                .customize(n -> n.tokenId(tokenId2).serialNumber(serialNumber).timestampRange(Range.closedOpen(4L, 5L)))
                .persist();
        expectedNftHistories.add(token2History3);
        // The migration will add this history entry to connect the two timestamp_ranges
        var splicedHistory2 = domainBuilder
                .nftHistory()
                .customize(n -> n.accountId(token2History3.getAccountId())
                        .createdTimestamp(token2History3.getCreatedTimestamp())
                        .metadata(token2History3.getMetadata())
                        .serialNumber(token2History3.getSerialNumber())
                        .timestampRange(Range.closedOpen(3L, 4L))
                        .tokenId(token2History3.getTokenId()))
                .get();
        expectedNftHistories.add(splicedHistory2);
        expectedNftHistories.add(domainBuilder
                .nftHistory()
                .customize(n -> n.tokenId(tokenId2).serialNumber(serialNumber).timestampRange(Range.closedOpen(5L, 6L)))
                .persist());

        // A history without any other matching serial number entries
        expectedNftHistories.add(domainBuilder
                .nftHistory()
                .customize(n ->
                        n.tokenId(tokenId2).serialNumber(serialNumber + 1).timestampRange(Range.closedOpen(4L, 5L)))
                .persist());
        // A history without any other matching token id entries
        expectedNftHistories.add(domainBuilder
                .nftHistory()
                .customize(n ->
                        n.tokenId(tokenId2 + 1).serialNumber(serialNumber).timestampRange(Range.closedOpen(4L, 5L)))
                .persist());

        // An nft that has a history that has a missing timestamp range
        var nft = domainBuilder
                .nft()
                .customize(n -> n.timestampRange(Range.atLeast(10L)))
                .persist();
        var nftHistory = domainBuilder
                .nftHistory()
                .customize(n -> n.accountId(nft.getAccountId())
                        .createdTimestamp(nft.getCreatedTimestamp())
                        .metadata(nft.getMetadata())
                        .serialNumber(nft.getSerialNumber())
                        .timestampRange(Range.closedOpen(1L, 8L))
                        .tokenId(nft.getTokenId()))
                .persist();
        expectedNftHistories.add(nftHistory);
        var splicedNftHistory3 = domainBuilder
                .nftHistory()
                .customize(n -> n.accountId(nftHistory.getAccountId())
                        .createdTimestamp(nftHistory.getCreatedTimestamp())
                        .metadata(nftHistory.getMetadata())
                        .serialNumber(nftHistory.getSerialNumber())
                        .timestampRange(Range.closedOpen(8L, 10L))
                        .tokenId(nftHistory.getTokenId()))
                .get();
        expectedNftHistories.add(splicedNftHistory3);

        // An nft that does not have any missing history
        var nft2 = domainBuilder
                .nft()
                .customize(n -> n.timestampRange(Range.atLeast(10L)))
                .persist();
        expectedNftHistories.add(domainBuilder
                .nftHistory()
                .customize(n -> n.accountId(nft2.getAccountId())
                        .createdTimestamp(nft2.getCreatedTimestamp())
                        .metadata(nft2.getMetadata())
                        .serialNumber(nft2.getSerialNumber())
                        .timestampRange(Range.closedOpen(1L, 10L))
                        .tokenId(nft.getTokenId()))
                .persist());

        // when
        runMigration();

        // then
        assertThat(findAllNftHistories()).containsExactlyInAnyOrderElementsOf(expectedNftHistories);
    }

    protected Iterable<NftHistory> findAllNftHistories() {
        return jdbcOperations.query("select * from nft_history", NFT_HISTORY_ROW_MAPPER);
    }

    @SneakyThrows
    private void runMigration() {
        jdbcOperations.update(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }
}
