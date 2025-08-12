// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import com.hederahashgraph.api.proto.java.Key;
import jakarta.inject.Named;
import java.io.IOException;
import java.util.Map;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.importer.ImporterProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

@Named
final class ContractNullKeyMigration extends ConfigurableJavaMigration {

    private final ObjectProvider<NamedParameterJdbcOperations> jdbcOperationsProvider;
    private final boolean v2;

    public ContractNullKeyMigration(
            Environment environment,
            ObjectProvider<NamedParameterJdbcOperations> jdbcOperationsProvider,
            ImporterProperties importerProperties) {
        super(importerProperties.getMigration());
        this.jdbcOperationsProvider = jdbcOperationsProvider;
        this.v2 = environment.acceptsProfiles(Profiles.of("v2"));
    }

    @Override
    public MigrationVersion getVersion() {
        return v2 ? MigrationVersion.fromVersion("2.12.0") : MigrationVersion.fromVersion("1.107.0");
    }

    @Override
    public String getDescription() {
        return "Populates a default ContractID key for contracts with a missing key";
    }

    @Override
    protected void doMigrate() throws IOException {
        update(false);
        update(true);
    }

    private void update(boolean history) {
        String suffix = history ? "_history" : "";
        var query = String.format(
                "select id from entity%s where key is null and type = 'CONTRACT' and created_timestamp is not null",
                suffix);
        var update = String.format("update entity%s set key = :key where id = :id", suffix);

        final var jdbcOperations = jdbcOperationsProvider.getObject();
        jdbcOperations.query(query, rs -> {
            var id = EntityId.of(rs.getLong(1));
            byte[] key =
                    Key.newBuilder().setContractID(id.toContractID()).build().toByteArray();
            jdbcOperations.update(update, Map.of("key", key, "id", id.getId()));
        });
    }
}
