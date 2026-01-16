// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.hiero.mirror.common.domain.contract.ContractState;
import org.hiero.mirror.common.domain.contract.ContractStateChange;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.EnabledIfV1;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.repository.ContractStateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;

@DisablePartitionMaintenance
@EnabledIfV1
@RequiredArgsConstructor
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.67.0")
@DisableRepeatableSqlMigration
class ContractStateMigrationTest extends ImporterIntegrationTest {

    private static final String REVERT_SQL = """
            drop table if exists contract_state;
            """;

    @Value("classpath:db/migration/v1/V1.67.1__contract_state.sql")
    private final File migrationSql;

    private final ContractStateRepository contractStateRepository;

    @AfterEach
    @SneakyThrows
    void teardown() {
        ownerJdbcTemplate.execute(REVERT_SQL);
    }

    @Test
    void empty() {
        runMigration();
        assertThat(contractStateRepository.findAll()).isEmpty();
    }

    @Test
    void migrate() {
        // given
        var builder = domainBuilder.contractStateChange().customize(c -> c.consensusTimestamp(1L)
                .migration(true)
                .contractId(1000)
                .slot(new byte[] {1})
                .valueRead("a".getBytes())
                .valueWritten(null));

        builder.persist();
        var contractStateChange2 = builder.customize(
                        c -> c.consensusTimestamp(2L).valueRead("b".getBytes()))
                .persist();
        builder.customize(c -> c.slot(new byte[] {2}).valueRead("c".getBytes())).persist();
        var contractStateChange4 = builder.customize(c -> c.contractId(1001)
                        .consensusTimestamp(2L)
                        .slot(new byte[] {1})
                        .valueRead("d".getBytes()))
                .persist();
        var contractStateChange5 = builder.customize(c -> c.contractId(1000)
                        .consensusTimestamp(3L)
                        .migration(false)
                        .slot(new byte[] {2})
                        .valueRead("c".getBytes())
                        .valueWritten("e".getBytes()))
                .persist();
        builder.customize(c -> c.contractId(1001)
                        .consensusTimestamp(4L)
                        .migration(false)
                        .slot(new byte[] {1})
                        .valueRead("f".getBytes())
                        .valueWritten(null))
                .persist();

        // when
        runMigration();

        var expected = new ArrayList<ContractState>();
        expected.add(this.convert(contractStateChange2, 1L));
        expected.add(this.convert(contractStateChange4, 2L));
        expected.add(this.convert(contractStateChange5, 2L));

        // then
        assertThat(contractStateRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
    }

    private ContractState convert(ContractStateChange contractStateChange, long createdTimestamp) {
        var value = contractStateChange.getValueWritten() == null
                ? contractStateChange.getValueRead()
                : contractStateChange.getValueWritten();
        return ContractState.builder()
                .contractId(contractStateChange.getContractId())
                .createdTimestamp(createdTimestamp)
                .modifiedTimestamp(contractStateChange.getConsensusTimestamp())
                .slot(DomainUtils.leftPadBytes(contractStateChange.getSlot(), 32))
                .value(value)
                .build();
    }

    @SneakyThrows
    private void runMigration() {
        ownerJdbcTemplate.update(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }
}
