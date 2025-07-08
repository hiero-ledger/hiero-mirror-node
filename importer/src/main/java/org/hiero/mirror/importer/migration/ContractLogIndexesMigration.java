// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import com.google.common.base.Stopwatch;
import jakarta.inject.Named;
import java.util.Map;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

@Named
public class ContractLogIndexesMigration extends AbstractJavaMigration {

    // Recalculate contract log indexes on block level.
    private static final String MIGRATION_SQL =
            """
                update contract_log cl
                    set
                        "index" = subquery.new_calculated_index
                    from
                        (
                            select
                                cl_inner.consensus_timestamp,
                                cl_inner.index as old_contract_log_index,
                                (row_number() over (
                                    partition by rf.index
                                    order by cl_inner.consensus_timestamp asc, cl_inner.index asc
                                ) - 1) as new_calculated_index
                            from
                                contract_log cl_inner
                            join
                                record_file rf on cl_inner.consensus_timestamp >= rf.consensus_start
                                              and cl_inner.consensus_timestamp <= rf.consensus_end
                        ) as subquery
                    where
                        cl.consensus_timestamp = subquery.consensus_timestamp
                        and cl.index = subquery.old_contract_log_index;
            """;

    private final NamedParameterJdbcOperations jdbcOperations;
    private final boolean v2;

    @Lazy
    public ContractLogIndexesMigration(
            final Environment environment, final NamedParameterJdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
        this.v2 = environment.acceptsProfiles(Profiles.of("v2"));
    }

    @Override
    protected void doMigrate() {
        var stopwatch = Stopwatch.createStarted();

        int count = jdbcOperations.update(MIGRATION_SQL, Map.of());
        log.info("Recalculate contract log indexes for {} entities in {}", count, stopwatch);
    }

    @Override
    public MigrationVersion getVersion() {
        return v2 ? MigrationVersion.fromVersion("2.15.1") : MigrationVersion.fromVersion("1.110.1");
    }

    @Override
    public String getDescription() {
        return "Recalculate contract log indexes on block level.";
    }
}
