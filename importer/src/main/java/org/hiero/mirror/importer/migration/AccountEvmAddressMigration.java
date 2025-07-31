// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import jakarta.inject.Named;
import java.io.IOException;
import java.util.Map;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.util.Utility;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

@Named
public class AccountEvmAddressMigration extends RepeatableMigration {

    private final ObjectProvider<NamedParameterJdbcOperations> jdbcOperationsProvider;

    public AccountEvmAddressMigration(
            ObjectProvider<NamedParameterJdbcOperations> jdbcOperationsProvider,
            ImporterProperties importerProperties) {
        super(importerProperties.getMigration());
        this.jdbcOperationsProvider = jdbcOperationsProvider;
    }

    @Override
    protected void doMigrate() throws IOException {
        updateAlias(false);
        updateAlias(true);
    }

    // We search for aliases with a length of 35 since ECDSA secp256k1 aliases are 33 bytes w/ 2 bytes for proto prefix
    private void updateAlias(boolean history) {
        String suffix = history ? "_history" : "";
        var query = String.format(
                "select id, alias from entity%s where evm_address is null and length(alias) = 35", suffix);
        var update = String.format("update entity%s set evm_address = :evmAddress where id = :id", suffix);

        jdbcOperationsProvider.getObject().query(query, rs -> {
            long id = rs.getLong(1);
            byte[] alias = rs.getBytes(2);
            byte[] evmAddress = Utility.aliasToEvmAddress(alias);

            if (evmAddress != null) {
                jdbcOperationsProvider.getObject().update(update, Map.of("evmAddress", evmAddress, "id", id));
            }
        });
    }

    @Override
    public String getDescription() {
        return "Populates evm_address for accounts with an ECDSA secp256k1 alias";
    }

    @Override
    public MigrationVersion getMinimumVersion() {
        return MigrationVersion.fromVersion("1.58.6");
    }
}
