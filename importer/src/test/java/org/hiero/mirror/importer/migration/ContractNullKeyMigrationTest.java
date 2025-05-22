// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.hederahashgraph.api.proto.java.Key;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcOperations;

@RequiredArgsConstructor
@Tag("migration")
class ContractNullKeyMigrationTest extends ImporterIntegrationTest {

    private final JdbcOperations jdbcOperations;
    private final ContractNullKeyMigration migration;

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void migrateSuccessful(boolean history) throws Exception {
        // Given
        var key = domainBuilder.key();
        insertEntity(history, 100L, 1L, null, EntityType.CONTRACT);
        insertEntity(history, 101L, 2L, null, EntityType.CONTRACT);
        insertEntity(history, 102L, 3L, null, EntityType.ACCOUNT);
        insertEntity(history, null, 4L, null, EntityType.CONTRACT);
        insertEntity(history, 104L, 5L, key, EntityType.CONTRACT);

        // When
        migration.doMigrate();

        // Then
        assertThat(findKey(history, 1L)).isEqualTo(asContractIdKey(1L));
        assertThat(findKey(history, 2L)).isEqualTo(asContractIdKey(2L));
        assertThat(findKey(history, 3L)).isEqualTo(null);
        assertThat(findKey(history, 4L)).isEqualTo(null);
        assertThat(findKey(history, 5L)).isEqualTo(key);
    }

    private byte[] asContractIdKey(long id) {
        var entityId = EntityId.of(id);
        return Key.newBuilder().setContractID(entityId.toContractID()).build().toByteArray();
    }

    private byte[] findKey(boolean history, long id) {
        var suffix = history ? "_history" : "";
        var query = String.format("select key from entity%s where id = ?", suffix);
        return jdbcOperations.queryForObject(query, byte[].class, id);
    }

    private void insertEntity(boolean history, Long createdTimestamp, long id, byte[] key, EntityType type) {
        var suffix = history ? "_history" : "";
        var sql = String.format(
                "insert into entity%s (created_timestamp, id, key, num, realm, shard, timestamp_range, type) "
                        + "values (?,?,?,?,0,0,'[1,)',?::entity_type)",
                suffix);
        jdbcOperations.update(sql, createdTimestamp, id, key, id, type.toString());
    }
}
