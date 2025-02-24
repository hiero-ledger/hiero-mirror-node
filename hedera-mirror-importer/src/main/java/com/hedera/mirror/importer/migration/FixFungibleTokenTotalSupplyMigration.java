// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.migration;

import com.google.common.base.Stopwatch;
import com.hedera.mirror.importer.ImporterProperties;
import jakarta.inject.Named;
import java.io.IOException;
import lombok.CustomLog;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;

@CustomLog
@Named
public class FixFungibleTokenTotalSupplyMigration extends RepeatableMigration {

    private static final String SQL =
            """
            with last as (
              select max(consensus_end) as consensus_end from record_file
            ), snapshot_timestamp as (
              select max(consensus_timestamp) as timestamp
              from account_balance
              where consensus_timestamp <= (select consensus_end from last)
                and consensus_timestamp > (select consensus_end from last) - 2678400000000000
               and account_id = 2
            ), token_balance_snapshot as (
              select distinct on (account_id, token_id) token_id, balance
              from token_balance
              where consensus_timestamp <= (select timestamp from snapshot_timestamp)
                and consensus_timestamp > (select timestamp from snapshot_timestamp) - 2678400000000000
              order by account_id, token_id, consensus_timestamp desc
            ), token_balance_sum as (
              select token_id, sum(balance) as amount
              from token_balance_snapshot
              group by token_id
            ), initial as (
              select tb.*
              from token_balance_sum as tb
              join token as tk using (token_id)
              where tk.type = 'FUNGIBLE_COMMON'
            ), change as (
              select token_id, sum(amount) as amount
              from token_transfer
              where consensus_timestamp > (select timestamp from snapshot_timestamp)
              group by token_id
            ), final as (
              select
                coalesce(i.token_id, c.token_id) as token_id,
                coalesce(i.amount, 0) + coalesce(c.amount, 0) as amount
              from initial as i
              full outer join change as c using (token_id)
            )
            update token as t
            set total_supply = amount
            from final as f
            where t.token_id = f.token_id
            """;

    private final JdbcTemplate jdbcTemplate;

    @Lazy
    public FixFungibleTokenTotalSupplyMigration(JdbcTemplate jdbcTemplate, ImporterProperties importerProperties) {
        super(importerProperties.getMigration());
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected void doMigrate() throws IOException {
        var stopwatch = Stopwatch.createStarted();
        int count = jdbcTemplate.update(SQL);
        log.info("Fixed {} fungible tokens' total supply in {}", count, stopwatch);
    }

    @Override
    public String getDescription() {
        return "Fix fungible token total supply";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        return MigrationVersion.fromVersion("1.89.2"); // the version which dedups balance tables
    }
}
