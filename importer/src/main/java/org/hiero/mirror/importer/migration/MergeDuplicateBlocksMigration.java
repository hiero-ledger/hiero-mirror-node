// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import com.google.common.base.Stopwatch;
import jakarta.inject.Named;
import java.io.IOException;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.config.Owner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcOperations;

@Named
class MergeDuplicateBlocksMigration extends RepeatableMigration {

    private static final String SQL = """
                    with block1 as (
                      delete from record_file
                      where consensus_end = 1675962000231859003 and index = 44029066
                      returning *
                    ),
                    merged_block as (
                      update record_file block2 set
                      consensus_start = block1.consensus_start,
                      count = block1.count + block2.count,
                      gas_used = block1.gas_used + block2.gas_used,
                      load_start = block1.load_start,
                      name = block1.name,
                      prev_hash = block1.prev_hash,
                      sidecar_count = block1.sidecar_count + block2.sidecar_count,
                      size = block1.size + block2.size
                      from block1
                      where block2.consensus_end = 1675962001984524003 and block1.index = block2.index
                      returning block2.*
                    )
                    update transaction t
                    set index = t.index + block1.count
                    from block1
                    where consensus_timestamp > 1675962000231859003 and consensus_timestamp <= 1675962001984524003;
                    """;

    private final ObjectProvider<JdbcOperations> jdbcOperationsProvider;
    private final ImporterProperties importerProperties;

    protected MergeDuplicateBlocksMigration(
            @Owner ObjectProvider<JdbcOperations> jdbcOperationsProvider, ImporterProperties importerProperties) {
        super(importerProperties.getMigration());
        this.jdbcOperationsProvider = jdbcOperationsProvider;
        this.importerProperties = importerProperties;
    }

    @Override
    protected void doMigrate() throws IOException {
        if (!ImporterProperties.HederaNetwork.MAINNET.equalsIgnoreCase(importerProperties.getNetwork())) {
            return;
        }

        var stopwatch = Stopwatch.createStarted();
        int count = jdbcOperationsProvider.getObject().update(SQL);
        log.info("Successfully merged the blocks and fixed {} transaction indexes in {}", count, stopwatch);
    }

    @Override
    public String getDescription() {
        return "Fix duplicate block number issue on mainnet by merging them";
    }
}
