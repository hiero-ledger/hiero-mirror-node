// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.hiero.mirror.common.converter.EntityIdConverter;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.Nft;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.EnabledIfV1;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.repository.NftRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.StreamUtils;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@EnabledIfV1
@RequiredArgsConstructor
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.81.0")
class AddNftHistoryMigrationTest extends ImporterIntegrationTest {

    private static final String REVERT_DDL = """
                    create table if not exists nft_transfer (
                      consensus_timestamp bigint not null,
                      is_approval         boolean,
                      payer_account_id    bigint not null,
                      receiver_account_id bigint,
                      sender_account_id   bigint,
                      serial_number       bigint not null,
                      token_id            bigint not null
                    );
                    create index if not exists nft_transfer__timestamp on nft_transfer (consensus_timestamp);
                    create index if not exists nft_transfer__token_serial_timestamp on nft_transfer (token_id, serial_number, consensus_timestamp);
                    truncate nft;
                    alter table if exists nft
                      drop column timestamp_range,
                      add column modified_timestamp bigint not null;
                    drop table if exists nft_history;
                    """;

    private final NftRepository nftRepository;

    @Value("classpath:db/migration/v1/V1.81.1__add_nft_history.sql")
    private final Resource sql;

    @AfterEach
    void teardown() {
        ownerJdbcTemplate.execute(REVERT_DDL);
    }

    @Test
    void empty() {
        runMigration();
        assertThat(nftRepository.findAll()).isEmpty();
        assertThat(findHistory(Nft.class)).isEmpty();
    }

    @Test
    void migrate() {
        // given
        var expectedNftHistory = new ArrayList<Nft>();
        var nftTransfers = new ArrayList<MigrationNftTransfer>();

        // nft1 is minted to treasury, transferred to Alice, then transferred to Bob
        long nft1Treasury = domainBuilder.id();
        long alice = domainBuilder.id();
        long bob = domainBuilder.id();

        // mint
        var nftTransfer = MigrationNftTransfer.builder()
                .consensusTimestamp(domainBuilder.timestamp())
                .receiverAccountId(nft1Treasury)
                .serialNumber(domainBuilder.number())
                .tokenId(domainBuilder.id())
                .build();
        long nextTimestamp = nftTransfer.getConsensusTimestamp() + 10L;
        var nft1 = Nft.builder()
                .accountId(EntityId.of(nft1Treasury))
                .createdTimestamp(nftTransfer.getConsensusTimestamp())
                .deleted(false)
                .metadata(domainBuilder.bytes(16))
                .serialNumber(nftTransfer.getSerialNumber())
                .timestampRange(Range.closedOpen(nftTransfer.getConsensusTimestamp(), nextTimestamp))
                .tokenId(nftTransfer.getTokenId())
                .build();
        expectedNftHistory.add(nft1);
        nftTransfers.add(nftTransfer);

        // treasury -> Alice
        nftTransfer = nftTransfer.toBuilder()
                .consensusTimestamp(nextTimestamp)
                .receiverAccountId(alice)
                .senderAccountId(nft1Treasury)
                .build();
        nextTimestamp += 10;
        expectedNftHistory.add(nft1.toBuilder()
                .accountId(EntityId.of(alice))
                .timestampRange(Range.closedOpen(nftTransfer.getConsensusTimestamp(), nextTimestamp))
                .build());
        nftTransfers.add(nftTransfer);

        // Alice -> bob
        nftTransfers.add(nftTransfer.toBuilder()
                .consensusTimestamp(nextTimestamp)
                .receiverAccountId(bob)
                .senderAccountId(alice)
                .build());
        var expectedNft1 = nft1.toBuilder()
                .accountId(EntityId.of(bob))
                .timestampRange(Range.atLeast(nextTimestamp))
                .build();

        // nft2, mint burn
        long nft2Treasury = domainBuilder.id();
        nftTransfer = MigrationNftTransfer.builder()
                .consensusTimestamp(domainBuilder.timestamp())
                .receiverAccountId(nft2Treasury)
                .serialNumber(domainBuilder.number())
                .tokenId(domainBuilder.id())
                .build();
        nextTimestamp = nftTransfer.getConsensusTimestamp() + 15L;
        var nft2 = Nft.builder()
                .accountId(EntityId.of(nft2Treasury))
                .createdTimestamp(nftTransfer.getConsensusTimestamp())
                .deleted(false)
                .metadata(domainBuilder.bytes(16))
                .serialNumber(nftTransfer.getSerialNumber())
                .timestampRange(Range.closedOpen(nftTransfer.getConsensusTimestamp(), nextTimestamp))
                .tokenId(nftTransfer.getTokenId())
                .build();
        expectedNftHistory.add(nft2);
        nftTransfers.add(nftTransfer);

        // burn nft2
        nftTransfer = nftTransfer.toBuilder()
                .consensusTimestamp(nextTimestamp)
                .senderAccountId(nft2Treasury)
                .build();
        nftTransfers.add(nftTransfer);
        var expectedNft2 = nft2.toBuilder()
                .accountId(null)
                .deleted(true)
                .timestampRange(Range.atLeast(nextTimestamp))
                .build();

        // nft 3, just a mint transfer, with delegating spender and spender
        long nft3Treasury = domainBuilder.id();
        nftTransfer = MigrationNftTransfer.builder()
                .consensusTimestamp(domainBuilder.timestamp())
                .receiverAccountId(nft3Treasury)
                .serialNumber(domainBuilder.number())
                .tokenId(domainBuilder.id())
                .build();
        var expectedNft3 = Nft.builder()
                .accountId(EntityId.of(nft3Treasury))
                .createdTimestamp(nftTransfer.getConsensusTimestamp())
                .delegatingSpender(domainBuilder.id())
                .deleted(false)
                .metadata(domainBuilder.bytes(16))
                .serialNumber(nftTransfer.getSerialNumber())
                .spender(domainBuilder.id())
                .timestampRange(Range.atLeast(nftTransfer.getConsensusTimestamp()))
                .tokenId(nftTransfer.getTokenId())
                .build();
        nftTransfers.add(nftTransfer);

        persistNftTransfers(nftTransfers);
        persistNfts(Stream.of(expectedNft1, expectedNft2, expectedNft3)
                .map(MigrationNft::fromDomainNft)
                .toList());

        // when
        runMigration();

        // then
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(expectedNft1, expectedNft2, expectedNft3);
        assertThat(findHistory(Nft.class)).containsExactlyInAnyOrderElementsOf(expectedNftHistory);
    }

    private void persistNftTransfers(List<MigrationNftTransfer> nftTransfers) {
        jdbcOperations.batchUpdate(
                """
                        insert into nft_transfer (consensus_timestamp, receiver_account_id, sender_account_id,
                          serial_number, token_id, payer_account_id)
                        values (?, ?, ?, ?, ?, 5000)
                        """,
                nftTransfers.stream()
                        .map(nftTransfer -> new Object[] {
                            nftTransfer.getConsensusTimestamp(),
                            nftTransfer.getReceiverAccountId(),
                            nftTransfer.getSenderAccountId(),
                            nftTransfer.getSerialNumber(),
                            nftTransfer.getTokenId()
                        })
                        .toList());
    }

    private void persistNfts(List<MigrationNft> nfts) {
        jdbcOperations.batchUpdate(
                """
                        insert into nft (account_id, created_timestamp, delegating_spender, deleted, modified_timestamp,
                          metadata, serial_number, spender, token_id)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                nfts.stream()
                        .map(nft -> new Object[] {
                            nft.getAccountId(),
                            nft.getCreatedTimestamp(),
                            nft.getDelegatingSpender(),
                            nft.getDeleted(),
                            nft.getModifiedTimestamp(),
                            nft.getMetadata(),
                            nft.getSerialNumber(),
                            nft.getSpender(),
                            nft.getTokenId()
                        })
                        .toList());
    }

    @SneakyThrows
    private void runMigration() {
        try (var is = sql.getInputStream()) {
            ownerJdbcTemplate.update(StreamUtils.copyToString(is, StandardCharsets.UTF_8));
        }
    }

    @AllArgsConstructor
    @Builder
    @Data
    private static class MigrationNft {
        private Long accountId;
        private Long createdTimestamp;
        private Boolean deleted;
        private Long delegatingSpender;
        private byte[] metadata;
        private long modifiedTimestamp;
        private long serialNumber;
        private Long spender;
        private long tokenId;

        public static MigrationNft fromDomainNft(Nft nft) {
            return MigrationNft.builder()
                    .accountId(EntityIdConverter.INSTANCE.convertToDatabaseColumn(nft.getAccountId()))
                    .createdTimestamp(nft.getCreatedTimestamp())
                    .deleted(nft.getDeleted())
                    .delegatingSpender(nft.getDelegatingSpender())
                    .metadata(nft.getMetadata())
                    .modifiedTimestamp(nft.getTimestampLower())
                    .spender(nft.getSpender())
                    .serialNumber(nft.getSerialNumber())
                    .tokenId(nft.getTokenId())
                    .build();
        }
    }

    @AllArgsConstructor
    @Builder(toBuilder = true)
    @Data
    private static class MigrationNftTransfer {
        long consensusTimestamp;
        Long receiverAccountId;
        Long senderAccountId;
        long serialNumber;
        long tokenId;
    }
}
