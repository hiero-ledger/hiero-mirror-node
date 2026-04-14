// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.hiero.mirror.importer.EnabledIfV1;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.repository.ContractLogRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

@EnabledIfV1
@RequiredArgsConstructor
@Tag("migration")
class RemoveDuplicatedTransferSyntheticContractLogsMigrationTest extends ImporterIntegrationTest {

    private static final long MIGRATION_TIMESTAMP_THRESHOLD = 177231600000000000L;

    @Value("classpath:db/migration/v1/V1.121.0__remove_duplicated_transfer_synthetic_contract_logs.sql")
    private final Resource migrationSql;

    private final ContractLogRepository contractLogRepository;

    @Test
    void empty() {
        runMigration();
        assertThat(contractLogRepository.findAll()).isEmpty();
    }

    @Test
    void noDuplicates() {
        var recordFile = domainBuilder
                .recordFile()
                .customize(rf -> rf.hapiVersionMajor(0)
                        .hapiVersionMinor(71)
                        .hapiVersionPatch(0)
                        .consensusStart(MIGRATION_TIMESTAMP_THRESHOLD + 1000))
                .persist();

        var consensusTimestamp = recordFile.getConsensusStart() + 100;
        var transactionHash = domainBuilder.bytes(32);

        var contractLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(consensusTimestamp)
                        .index(0)
                        .transactionHash(transactionHash)
                        .transactionIndex(0))
                .persist();

        runMigration();

        assertThat(contractLogRepository.findAll()).hasSize(1).containsExactly(contractLog);
    }

    @Test
    void removeDuplicateWithSameTopicsAndData() throws DecoderException {
        var recordFile = domainBuilder
                .recordFile()
                .customize(rf -> rf.hapiVersionMajor(0)
                        .hapiVersionMinor(71)
                        .hapiVersionPatch(0)
                        .consensusStart(MIGRATION_TIMESTAMP_THRESHOLD + 1000))
                .persist();

        var consensusTimestamp = recordFile.getConsensusStart() + 100;
        var contractId = domainBuilder.entityId();
        var topic0 = Hex.decodeHex("ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef");
        var topic1 = domainBuilder.bytes(32);
        var topic2 = domainBuilder.bytes(32);
        byte[] topic3 = null;
        var originalData = Hex.decodeHex("0000000000000000000000000000000000000000000000000000000000000064");
        var duplicateData = Hex.decodeHex("64");
        var bloomOriginal = domainBuilder.bytes(256);
        var bloomDuplicate = domainBuilder.bytes(256);
        var transactionHashOriginal = domainBuilder.bytes(32);
        var transactionHashDuplicate = domainBuilder.bytes(32);

        var originalLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(consensusTimestamp)
                        .contractId(contractId)
                        .index(0)
                        .topic0(topic0)
                        .topic1(topic1)
                        .topic2(topic2)
                        .topic3(topic3)
                        .data(originalData)
                        .bloom(bloomOriginal)
                        .transactionHash(transactionHashOriginal)
                        .transactionIndex(0))
                .persist();

        domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(consensusTimestamp)
                        .contractId(contractId)
                        .index(1)
                        .topic0(topic0)
                        .topic1(topic1)
                        .topic2(topic2)
                        .topic3(topic3)
                        .data(duplicateData)
                        .bloom(bloomDuplicate)
                        .transactionHash(transactionHashDuplicate)
                        .transactionIndex(1))
                .persist();

        runMigration();

        assertThat(contractLogRepository.findAll()).hasSize(1).containsExactly(originalLog);
    }

    @Test
    void removeDuplicateWithNullTopics() throws DecoderException {
        var recordFile = domainBuilder
                .recordFile()
                .customize(rf -> rf.hapiVersionMajor(0)
                        .hapiVersionMinor(71)
                        .hapiVersionPatch(0)
                        .consensusStart(MIGRATION_TIMESTAMP_THRESHOLD + 1000))
                .persist();

        var consensusTimestamp = recordFile.getConsensusStart() + 100;
        var contractId = domainBuilder.entityId();
        var topic0 = Hex.decodeHex("ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef");
        var originalData = Hex.decodeHex("00000000000000000000000000000000000000000000000000000000000000c8");
        var duplicateData = Hex.decodeHex("c8");
        var bloomOriginal = domainBuilder.bytes(256);
        var bloomDuplicate = domainBuilder.bytes(256);
        var transactionHashOriginal = domainBuilder.bytes(32);
        var transactionHashDuplicate = domainBuilder.bytes(32);

        var originalLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(consensusTimestamp)
                        .contractId(contractId)
                        .index(0)
                        .topic0(topic0)
                        .topic1(null)
                        .topic2(null)
                        .topic3(null)
                        .data(originalData)
                        .bloom(bloomOriginal)
                        .transactionHash(transactionHashOriginal)
                        .transactionIndex(0))
                .persist();

        domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(consensusTimestamp)
                        .contractId(contractId)
                        .index(1)
                        .topic0(topic0)
                        .topic1(null)
                        .topic2(null)
                        .topic3(null)
                        .data(duplicateData)
                        .bloom(bloomDuplicate)
                        .transactionHash(transactionHashDuplicate)
                        .transactionIndex(1))
                .persist();

        runMigration();

        assertThat(contractLogRepository.findAll()).hasSize(1).containsExactly(originalLog);
    }

    @Test
    void keepBothWhenDifferentTopic1() throws DecoderException {
        var recordFile = domainBuilder
                .recordFile()
                .customize(rf -> rf.hapiVersionMajor(0)
                        .hapiVersionMinor(71)
                        .hapiVersionPatch(0)
                        .consensusStart(MIGRATION_TIMESTAMP_THRESHOLD + 1000))
                .persist();

        var consensusTimestamp = recordFile.getConsensusStart() + 100;
        var contractId = domainBuilder.entityId();
        var topic0 = Hex.decodeHex("ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef");
        var topic2 = domainBuilder.bytes(32);
        byte[] topic3 = null;
        var originalData = Hex.decodeHex("000000000000000000000000000000000000000000000000000000000000012c");
        var duplicateData = Hex.decodeHex("012c");
        var bloomOriginal = domainBuilder.bytes(256);
        var bloomDuplicate = domainBuilder.bytes(256);

        var log1 = domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(consensusTimestamp)
                        .contractId(contractId)
                        .index(0)
                        .topic0(topic0)
                        .topic1(domainBuilder.bytes(32))
                        .topic2(topic2)
                        .topic3(topic3)
                        .data(originalData)
                        .bloom(bloomOriginal)
                        .transactionHash(domainBuilder.bytes(32))
                        .transactionIndex(0))
                .persist();

        var log2 = domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(consensusTimestamp)
                        .contractId(contractId)
                        .index(1)
                        .topic0(topic0)
                        .topic1(domainBuilder.bytes(32))
                        .topic2(topic2)
                        .topic3(topic3)
                        .data(duplicateData)
                        .bloom(bloomDuplicate)
                        .transactionHash(domainBuilder.bytes(32))
                        .transactionIndex(1))
                .persist();

        runMigration();

        assertThat(contractLogRepository.findAll()).hasSize(2).containsExactlyInAnyOrder(log1, log2);
    }

    @Test
    void keepBothWhenDifferentData() throws DecoderException {
        var recordFile = domainBuilder
                .recordFile()
                .customize(rf -> rf.hapiVersionMajor(0)
                        .hapiVersionMinor(71)
                        .hapiVersionPatch(0)
                        .consensusStart(MIGRATION_TIMESTAMP_THRESHOLD + 1000))
                .persist();

        var consensusTimestamp = recordFile.getConsensusStart() + 100;
        var contractId = domainBuilder.entityId();
        var topic0 = Hex.decodeHex("ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef");
        var topic1 = domainBuilder.bytes(32);
        var topic2 = domainBuilder.bytes(32);
        byte[] topic3 = null;
        // Different data values - original: 400 (0x190), duplicate: 500 (0x1f4)
        // These should NOT match after LTRIM
        var originalData = Hex.decodeHex("0000000000000000000000000000000000000000000000000000000000000190");
        var duplicateData = Hex.decodeHex("00000000000000000000000000000000000001f4");
        var bloomOriginal = domainBuilder.bytes(256);
        var bloomDuplicate = domainBuilder.bytes(256);

        var log1 = domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(consensusTimestamp)
                        .contractId(contractId)
                        .index(0)
                        .topic0(topic0)
                        .topic1(topic1)
                        .topic2(topic2)
                        .topic3(topic3)
                        .data(originalData)
                        .bloom(bloomOriginal)
                        .transactionHash(domainBuilder.bytes(32))
                        .transactionIndex(0))
                .persist();

        var log2 = domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(consensusTimestamp)
                        .contractId(contractId)
                        .index(1)
                        .topic0(topic0)
                        .topic1(topic1)
                        .topic2(topic2)
                        .topic3(topic3)
                        .data(duplicateData)
                        .bloom(bloomDuplicate)
                        .transactionHash(domainBuilder.bytes(32))
                        .transactionIndex(1))
                .persist();

        runMigration();

        assertThat(contractLogRepository.findAll()).hasSize(2).containsExactlyInAnyOrder(log1, log2);
    }

    @Test
    void keepBothWhenSameTransactionHash() throws DecoderException {
        var recordFile = domainBuilder
                .recordFile()
                .customize(rf -> rf.hapiVersionMajor(0)
                        .hapiVersionMinor(71)
                        .hapiVersionPatch(0)
                        .consensusStart(MIGRATION_TIMESTAMP_THRESHOLD + 1000))
                .persist();

        var consensusTimestamp = recordFile.getConsensusStart() + 100;
        var contractId = domainBuilder.entityId();
        var topic0 = Hex.decodeHex("ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef");
        var topic1 = domainBuilder.bytes(32);
        var topic2 = domainBuilder.bytes(32);
        byte[] topic3 = null;
        var originalData = Hex.decodeHex("0000000000000000000000000000000000000000000000000000000000000258");
        var duplicateData = Hex.decodeHex("0258");
        var bloomOriginal = domainBuilder.bytes(256);
        var bloomDuplicate = domainBuilder.bytes(256);
        var transactionHash = domainBuilder.bytes(32);

        var log1 = domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(consensusTimestamp)
                        .contractId(contractId)
                        .index(0)
                        .topic0(topic0)
                        .topic1(topic1)
                        .topic2(topic2)
                        .topic3(topic3)
                        .data(originalData)
                        .bloom(bloomOriginal)
                        .transactionHash(transactionHash)
                        .transactionIndex(0))
                .persist();

        var log2 = domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(consensusTimestamp)
                        .contractId(contractId)
                        .index(1)
                        .topic0(topic0)
                        .topic1(topic1)
                        .topic2(topic2)
                        .topic3(topic3)
                        .data(duplicateData)
                        .bloom(bloomDuplicate)
                        .transactionHash(transactionHash)
                        .transactionIndex(1))
                .persist();

        runMigration();

        assertThat(contractLogRepository.findAll()).hasSize(2).containsExactlyInAnyOrder(log1, log2);
    }

    @Test
    void skipWhenTimestampBelowThreshold() throws DecoderException {
        var recordFile = domainBuilder
                .recordFile()
                .customize(rf -> rf.hapiVersionMajor(0)
                        .hapiVersionMinor(71)
                        .hapiVersionPatch(0)
                        .consensusStart(MIGRATION_TIMESTAMP_THRESHOLD - 1000))
                .persist();

        var consensusTimestamp = recordFile.getConsensusStart() + 100;
        var contractId = domainBuilder.entityId();
        var topic0 = domainBuilder.bytes(32);
        var topic1 = domainBuilder.bytes(32);
        var topic2 = domainBuilder.bytes(32);
        var originalData = Hex.decodeHex("00000000000000000000000000000000000000000000000000000000000002bc");
        // Duplicate data: 20 bytes, same value = 700 (0x2bc)
        var duplicateData = Hex.decodeHex("02bc");
        var bloomOriginal = domainBuilder.bytes(256);
        var bloomDuplicate = domainBuilder.bytes(256);

        var log1 = domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(consensusTimestamp)
                        .contractId(contractId)
                        .index(0)
                        .topic0(topic0)
                        .topic1(topic1)
                        .topic2(topic2)
                        .data(originalData)
                        .bloom(bloomOriginal)
                        .transactionHash(domainBuilder.bytes(32))
                        .transactionIndex(0))
                .persist();

        var log2 = domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(consensusTimestamp)
                        .contractId(contractId)
                        .index(1)
                        .topic0(topic0)
                        .topic1(topic1)
                        .topic2(topic2)
                        .data(duplicateData)
                        .bloom(bloomDuplicate)
                        .transactionHash(domainBuilder.bytes(32))
                        .transactionIndex(1))
                .persist();

        runMigration();

        assertThat(contractLogRepository.findAll()).hasSize(2).containsExactlyInAnyOrder(log1, log2);
    }

    @Test
    void removeMultipleDuplicates() throws DecoderException {
        var recordFile = domainBuilder
                .recordFile()
                .customize(rf -> rf.hapiVersionMajor(0)
                        .hapiVersionMinor(71)
                        .hapiVersionPatch(0)
                        .consensusStart(MIGRATION_TIMESTAMP_THRESHOLD + 1000))
                .persist();

        var consensusTimestamp = recordFile.getConsensusStart() + 100;
        var contractId = domainBuilder.entityId();
        var topic0 = Hex.decodeHex("ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef");
        var topic1 = domainBuilder.bytes(32);
        var topic2 = domainBuilder.bytes(32);
        byte[] topic3 = null;
        var originalData = Hex.decodeHex("0000000000000000000000000000000000000000000000000000000000000320");
        var duplicateData = Hex.decodeHex("0320");
        var bloomOriginal = domainBuilder.bytes(256);
        var transactionHashOriginal = domainBuilder.bytes(32);

        var originalLog = domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(consensusTimestamp)
                        .contractId(contractId)
                        .index(0)
                        .topic0(topic0)
                        .topic1(topic1)
                        .topic2(topic2)
                        .topic3(topic3)
                        .data(originalData)
                        .bloom(bloomOriginal)
                        .transactionHash(transactionHashOriginal)
                        .transactionIndex(0))
                .persist();

        domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(consensusTimestamp)
                        .contractId(contractId)
                        .index(1)
                        .topic0(topic0)
                        .topic1(topic1)
                        .topic2(topic2)
                        .topic3(topic3)
                        .data(duplicateData)
                        .bloom(domainBuilder.bytes(256))
                        .transactionHash(domainBuilder.bytes(32))
                        .transactionIndex(1))
                .persist();

        domainBuilder
                .contractLog()
                .customize(cl -> cl.consensusTimestamp(consensusTimestamp)
                        .contractId(contractId)
                        .index(2)
                        .topic0(topic0)
                        .topic1(topic1)
                        .topic2(topic2)
                        .topic3(topic3)
                        .data(duplicateData)
                        .bloom(domainBuilder.bytes(256))
                        .transactionHash(domainBuilder.bytes(32))
                        .transactionIndex(2))
                .persist();

        runMigration();

        assertThat(contractLogRepository.findAll()).hasSize(1).containsExactly(originalLog);
    }

    @SneakyThrows
    private void runMigration() {
        try (var is = migrationSql.getInputStream()) {
            ownerJdbcTemplate.execute(StreamUtils.copyToString(is, StandardCharsets.UTF_8));
        }
    }
}
