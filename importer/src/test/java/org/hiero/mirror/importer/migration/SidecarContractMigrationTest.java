// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.hiero.mirror.common.util.DomainUtils.fromBytes;
import static org.hiero.mirror.importer.TestUtils.toContractId;

import com.hedera.services.stream.proto.ContractBytecode;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.contract.Contract;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityHistory;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.repository.ContractRepository;
import org.hiero.mirror.importer.repository.EntityHistoryRepository;
import org.hiero.mirror.importer.repository.EntityRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor
@Tag("migration")
class SidecarContractMigrationTest extends ImporterIntegrationTest {

    private final ContractRepository contractRepository;
    private final EntityHistoryRepository entityHistoryRepository;
    private final EntityRepository entityRepository;
    private final JdbcTemplate jdbcTemplate;
    private final SidecarContractMigration sidecarContractMigration;

    @Test
    void migrateWhenNull() {
        sidecarContractMigration.migrate(null);
        assertThat(contractRepository.findAll()).isEmpty();
        assertThat(entityRepository.findAll()).isEmpty();
        assertThat(entityHistoryRepository.findAll()).isEmpty();
    }

    @Test
    void migrateWhenEmpty() {
        sidecarContractMigration.migrate(Collections.emptyList());
        assertThat(contractRepository.findAll()).isEmpty();
        assertThat(entityRepository.findAll()).isEmpty();
        assertThat(entityHistoryRepository.findAll()).isEmpty();
    }

    @Test
    void migrateWhenMissingContract() {
        // given
        var runtimeBytecode = new byte[] {0, 1, 2, 3};
        var contract = domainBuilder
                .entity()
                .customize(e -> e.evmAddress(null).type(CONTRACT))
                .persist();
        var contractBytecode = ContractBytecode.newBuilder()
                .setContractId(toContractId(contract))
                .setRuntimeBytecode(fromBytes(runtimeBytecode))
                .build();

        // when
        sidecarContractMigration.migrate(List.of(contractBytecode));

        // then
        assertThat(contractRepository.findAll())
                .hasSize(1)
                .first()
                .returns(runtimeBytecode, Contract::getRuntimeBytecode)
                .returns(contract.getId(), Contract::getId);
    }

    @Test
    void migrateEntityTypeToContract() {
        // given
        var entities = new ArrayList<Entity>();
        var contracts = new ArrayList<Contract>();
        var contractBytecodesMap = new HashMap<Long, ContractBytecode>();
        var contractBytecodeBuilder = ContractBytecode.newBuilder();
        var expected = new TreeSet<Long>();

        for (int i = 0; i < 66000; i++) {
            var entity = domainBuilder.entity().get();
            var entityId = entity.getId();
            expected.add(entityId);

            entities.add(entity);
            contracts.add(
                    domainBuilder.contract().customize(c -> c.id(entityId)).get());
            contractBytecodesMap.put(
                    entityId,
                    contractBytecodeBuilder
                            .setContractId(entity.toEntityId().toContractID())
                            .setRuntimeBytecode(fromBytes(domainBuilder.bytes(4)))
                            .build());
        }

        persistEntities(entities);
        persistContracts(contracts);

        var contractBytecodes = contractBytecodesMap.values().stream().toList();

        // when
        sidecarContractMigration.migrate(contractBytecodes);

        // then
        assertThat(entityRepository.findAll()).extracting(Entity::getType).containsOnly(CONTRACT);
        assertThat(entityHistoryRepository.findAll())
                .extracting(EntityHistory::getType)
                .containsOnly(CONTRACT);

        var contractsIterator = contractRepository.findAll().iterator();
        var ids = new TreeSet<Long>();
        contractsIterator.forEachRemaining(savedContract -> {
            ids.add(savedContract.getId());
            var contractBytecode = contractBytecodesMap.get(savedContract.getId());
            assertThat(DomainUtils.toBytes(contractBytecode.getRuntimeBytecode()))
                    .isEqualTo(savedContract.getRuntimeBytecode());
        });
        assertThat(contractsIterator).isExhausted();
        assertThat(ids).isEqualTo(expected);
    }

    // These persist methods are not functionally necessary but greatly speed up bulk insertion.
    private void persistEntities(List<Entity> entities) {
        jdbcTemplate.batchUpdate(
                "insert into entity (id, num, realm, shard, timestamp_range, type) "
                        + "values (?, ?, ?, ?, ?::int8range, ?::entity_type)",
                entities,
                entities.size(),
                (ps, entity) -> {
                    ps.setLong(1, entity.getId());
                    ps.setLong(2, entity.getNum());
                    ps.setLong(3, entity.getRealm());
                    ps.setLong(4, entity.getShard());
                    ps.setString(5, PostgreSQLGuavaRangeType.INSTANCE.asString(entity.getTimestampRange()));
                    ps.setString(6, entity.getType().toString());
                });

        jdbcTemplate.batchUpdate(
                "insert into entity_history (id, num, realm, shard, timestamp_range, type) "
                        + "values (?, ?, ?, ?, ?::int8range, ?::entity_type)",
                entities,
                entities.size(),
                (ps, entity) -> {
                    ps.setLong(1, entity.getId());
                    ps.setLong(2, entity.getNum());
                    ps.setLong(3, entity.getRealm());
                    ps.setLong(4, entity.getShard());
                    ps.setString(5, PostgreSQLGuavaRangeType.INSTANCE.asString(entity.getTimestampRange()));
                    ps.setString(6, entity.getType().toString());
                });
    }

    private void persistContracts(List<Contract> contracts) {
        jdbcTemplate.batchUpdate(
                "insert into contract (id, runtime_bytecode) values (?, ?)",
                contracts,
                contracts.size(),
                (ps, contract) -> {
                    ps.setLong(1, contract.getId());
                    ps.setBytes(2, contract.getRuntimeBytecode());
                });
    }
}
