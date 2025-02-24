// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.migration;

import com.google.common.base.Stopwatch;
import com.hedera.mirror.importer.ImporterProperties;
import com.hedera.mirror.importer.config.Owner;
import jakarta.inject.Named;
import java.io.IOException;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;

@Named
class MergeDuplicateBlocksMigration extends RepeatableMigration {

    private static final String SQL =
            """
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

    private final JdbcTemplate jdbcTemplate;
    private final ImporterProperties importerProperties;

    @Lazy
    protected MergeDuplicateBlocksMigration(@Owner JdbcTemplate jdbcTemplate, ImporterProperties importerProperties) {
        super(importerProperties.getMigration());
        this.jdbcTemplate = jdbcTemplate;
        this.importerProperties = importerProperties;
    }

    @Override
    protected void doMigrate() throws IOException {
        if (!ImporterProperties.HederaNetwork.MAINNET.equalsIgnoreCase(importerProperties.getNetwork())) {
            return;
        }

        var stopwatch = Stopwatch.createStarted();
        int count = jdbcTemplate.update(SQL);
        log.info("Successfully merged the blocks and fixed {} transaction indexes in {}", count, stopwatch);
    }

    @Override
    public String getDescription() {
        return "Fix duplicate block number issue on mainnet by merging them";
    }
}
