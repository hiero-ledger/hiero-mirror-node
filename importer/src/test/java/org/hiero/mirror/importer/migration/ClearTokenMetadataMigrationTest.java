// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.EMPTY_BYTE_ARRAY;

import com.google.common.collect.Range;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.assertj.core.util.Lists;
import org.hiero.mirror.common.domain.token.Token;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.EnabledIfV1;
import org.hiero.mirror.importer.repository.RecordFileMigrationTest;
import org.hiero.mirror.importer.repository.TokenRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.StreamUtils;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@EnabledIfV1
@RequiredArgsConstructor
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.97.1")
class ClearTokenMetadataMigrationTest extends RecordFileMigrationTest {

    private static final long CREATE = -1;
    private static final Predicate<Token> IS_CURRENT = t -> t.getTimestampUpper() == null;
    private static final long LAST_HAPI_0_46_TIMESTAMP = 1709229598938101716L;

    @Value("classpath:db/migration/v1/V1.97.2__clear_token_metadata.sql")
    private final Resource migrationSql;

    private final TokenRepository tokenRepository;
    private List<Token> tokens = new ArrayList<>();
    private final Map<Long, Token> tokenState = new HashMap<>();

    @AfterEach
    void teardown() {
        tokens.clear();
        tokenState.clear();
    }

    @Test
    void empty() {
        runMigration();
        assertThat(tokenRepository.findAll()).isEmpty();
        assertThat(findHistory(Token.class)).isEmpty();
    }

    @CsvSource(textBlock = """
            -1, true
            0, true
            1, true
            2, true
            3, true
            4, true
            1, false
            3, false
            """)
    @ParameterizedTest
    void migrate(int firstRecordFileIndexToDelete, boolean isBackwards) {
        // given
        // reserve 50ns for 2 HAPI 0.46 record files with each spanning 10ns
        domainBuilder.resetTimestamp(LAST_HAPI_0_46_TIMESTAMP - 50);
        var recordFiles = Lists.newArrayList(persistRecordFile(46), persistRecordFile(46));
        domainBuilder.resetTimestamp(LAST_HAPI_0_46_TIMESTAMP);
        recordFiles.addAll(List.of(
                persistRecordFile(47), // index 2
                persistRecordFile(47),
                // there is no HAPI version 0.48.0 record file in the network because services still generates
                // them with HAPI version 0.47.0 in release 0.48.x
                persistRecordFile(49), // index 4
                persistRecordFile(49)));

        // token1 created in 0.46, without metadata / metadata key, and none of its updates will set the fields
        long timestamp = recordFiles.getFirst().getConsensusStart();
        long token1 = createOrUpdateToken(t -> t.metadata(null).metadataKey(null), CREATE, timestamp);

        // token2 created in 0.47, no metadata, with metadata key, however a later update in 0.47 will set metadata
        timestamp = recordFiles.get(2).getConsensusStart();
        long token2 = createOrUpdateToken(
                t -> t.metadata(EMPTY_BYTE_ARRAY).metadataKey(domainBuilder.key()), CREATE, timestamp);

        // an update to set token2's metadata and updates metadata key
        createOrUpdateToken(
                t -> t.metadata(domainBuilder.bytes(8)).metadataKey(domainBuilder.key()), token2, ++timestamp);

        // token3 created in 0.47 with metadata / metadata key
        long token3 = createOrUpdateToken(t -> {}, CREATE, ++timestamp);

        // token3 metadata updated
        createOrUpdateToken(t -> t.metadata(domainBuilder.bytes(16)), token3, ++timestamp);

        // token1 updated
        createOrUpdateToken(t -> {}, token1, ++timestamp);

        // corner case, token4 created at the last consensus timestamp of 0.47, with metadata / metadata key
        timestamp = recordFiles.get(3).getConsensusEnd();
        createOrUpdateToken(t -> {}, CREATE, timestamp);

        // token5 created in 0.49, without metadata / metadata key
        timestamp = recordFiles.get(4).getConsensusStart();
        long token5 = createOrUpdateToken(t -> t.metadata(EMPTY_BYTE_ARRAY).metadataKey(null), CREATE, timestamp);

        // token5 updated, still no metadata / metadata key
        createOrUpdateToken(t -> {}, token5, ++timestamp);

        // token6 created in 0.49 with metadata / metadata key
        createOrUpdateToken(t -> {}, CREATE, ++timestamp);

        // token1 updated
        createOrUpdateToken(t -> {}, token1, ++timestamp);

        // token7 created in the second 0.49 record file, with metadata / metadata key
        timestamp = recordFiles.get(5).getConsensusStart();
        createOrUpdateToken(t -> {}, CREATE, recordFiles.get(5).getConsensusStart());

        // token1 updated
        createOrUpdateToken(t -> {}, token1, ++timestamp);

        prune(firstRecordFileIndexToDelete, isBackwards, recordFiles);
        persistTokens();
        var partitioned = tokens.stream()
                .peek(t -> {
                    if (t.getTimestampLower() < recordFiles.get(4).getConsensusStart()) {
                        t.setMetadata(null);
                        t.setMetadataKey(null);
                    }
                })
                .collect(Collectors.partitioningBy(IS_CURRENT));
        var expectedCurrent = partitioned.get(true);
        var expectedHistory = partitioned.get(false);

        // when
        runMigration();

        // then
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedCurrent);
        assertThat(findHistory(Token.class)).containsExactlyInAnyOrderElementsOf(expectedHistory);
    }

    private long createOrUpdateToken(Consumer<Token.TokenBuilder<?, ?>> customizer, long tokenId, long timestamp) {
        Token.TokenBuilder<?, ?> builder;
        if (tokenState.containsKey(tokenId)) {
            // close timestamp range of the current state
            var current = tokenState.get(tokenId);
            current.setTimestampUpper(timestamp);

            builder = current.toBuilder();
        } else {
            builder = domainBuilder.token().get().toBuilder().createdTimestamp(timestamp);
        }

        customizer.accept(builder);
        var token = builder.timestampRange(Range.atLeast(timestamp)).build();

        // get the actual token id, the tokenId value passed in might be -1 to indicate new token creation
        tokenId = token.getTokenId();
        tokens.add(token);
        tokenState.put(tokenId, token);
        return tokenId;
    }

    private RecordFile persistRecordFile(int hapiVersionMinor) {
        var recordFile = domainBuilder
                .recordFile()
                .customize(r -> {
                    long consensusEnd = r.build().getConsensusStart() + 10;
                    r.consensusEnd(consensusEnd).hapiVersionMinor(hapiVersionMinor);
                })
                .get();
        persistRecordFile(recordFile);
        // advance more than 10ns so the next timestamp would not fall into the same record file
        for (int i = 0; i < 12; i++) {
            domainBuilder.timestamp();
        }

        return recordFile;
    }

    private void persistTokens() {
        var partitioned = tokens.stream().collect(Collectors.partitioningBy(IS_CURRENT));
        var current = partitioned.get(true);
        var history = partitioned.get(false);

        tokenRepository.saveAll(current);
        if (!history.isEmpty()) {
            jdbcOperations.batchUpdate("""
                insert into token_history (created_timestamp, decimals, fee_schedule_key, freeze_default, freeze_key,
                  freeze_status, initial_supply, kyc_key, kyc_status, max_supply, metadata, metadata_key, name,
                  pause_key, pause_status, supply_key, supply_type, symbol, timestamp_range, token_id, total_supply,
                  treasury_account_id, type, wipe_key)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::token_pause_status,?,?::token_supply_type,?,?::int8range,?,?,?,?::token_type,?)
                """, history, history.size(), (ps, token) -> {
                ps.setLong(1, token.getCreatedTimestamp());
                ps.setInt(2, token.getDecimals());
                ps.setBytes(3, token.getFeeScheduleKey());
                ps.setBoolean(4, token.getFreezeDefault());
                ps.setBytes(5, token.getFreezeKey());
                ps.setInt(6, token.getFreezeStatus().ordinal());
                ps.setLong(7, token.getInitialSupply());
                ps.setBytes(8, token.getKycKey());
                ps.setInt(9, token.getKycStatus().ordinal());
                ps.setLong(10, token.getMaxSupply());
                ps.setBytes(11, token.getMetadata());
                ps.setBytes(12, token.getMetadataKey());
                ps.setString(13, token.getName());
                ps.setBytes(14, token.getPauseKey());
                ps.setString(15, token.getPauseStatus().name());
                ps.setBytes(16, token.getSupplyKey());
                ps.setString(17, token.getSupplyType().name());
                ps.setString(18, token.getSymbol());
                ps.setString(19, PostgreSQLGuavaRangeType.INSTANCE.asString(token.getTimestampRange()));
                ps.setLong(20, token.getTokenId());
                ps.setLong(21, token.getTotalSupply());
                ps.setLong(22, token.getTreasuryAccountId().getId());
                ps.setString(23, token.getType().name());
                ps.setBytes(24, token.getWipeKey());
            });
        }
    }

    private void prune(int firstRecordFileIndexToDelete, boolean isBackwards, List<RecordFile> recordFiles) {
        try {
            var recordFile = recordFiles.get(firstRecordFileIndexToDelete);
            long consensusEnd = recordFile.getConsensusEnd();
            if (isBackwards) {
                jdbcOperations.update("delete from record_file where consensus_end <= ?", consensusEnd);
                tokens = tokens.stream()
                        .filter(t -> t.getTimestampLower() > consensusEnd)
                        .collect(Collectors.toList());
            } else {
                jdbcOperations.update("delete from record_file where consensus_end >= ?", consensusEnd);
                tokens = tokens.stream()
                        .filter(t -> t.getTimestampLower() < recordFile.getConsensusStart())
                        .collect(Collectors.toList());
            }
        } catch (IndexOutOfBoundsException e) {
            // do nothing
        }
    }

    @SneakyThrows
    private void runMigration() {
        try (var is = migrationSql.getInputStream()) {
            var script = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
            jdbcOperations.execute(script);
        }
    }
}
