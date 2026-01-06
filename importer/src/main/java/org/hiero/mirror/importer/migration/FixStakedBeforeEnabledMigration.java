// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import com.google.common.base.Stopwatch;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.util.Utility;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

@Named
@RequiredArgsConstructor()
public class FixStakedBeforeEnabledMigration extends AbstractJavaMigration {

    static final Long LAST_HAPI_26_RECORD_FILE_CONSENSUS_END_MAINNET = 1658419200981687000L;

    private static final String MIGRATION_SQL = """
                    --- The migration fixes the staking settings for accounts started to stake to a node before 0.27.x release.
                    with last_26_file as (
                      select index from record_file where consensus_end = :consensusEnd
                    ), possible as (
                    -- find accounts / contracts whose stake period start is still on or before the day 0.27.0 is deployed
                      select id,decline_reward,staked_node_id,stake_period_start,timestamp_range
                      from entity
                      where stake_period_start <= :epochDay and stake_period_start <> -1 and staked_node_id <> -1 and type in ('ACCOUNT', 'CONTRACT')
                    ), history as (
                    --- if the staking setting first occurs in the history table, find the oldest matching history row
                      select distinct on (h.id) h.id, h.stake_period_start, h.timestamp_range
                      from entity_history h
                      join possible p on p.id = h.id and p.stake_period_start = h.stake_period_start and p.staked_node_id = h.staked_node_id
                      order by h.id, h.timestamp_range
                    ), staked_before_alive as (
                    --- network 0.27.0 upgrade happened during a UTC day, make sure only fix such settings set at or before
                    --- the consensus end of the last HAPI 0.26.0 record file
                      select p.id as entity_id
                      from possible p
                      left join history h on h.id = p.id
                      where coalesce(lower(h.timestamp_range), lower(p.timestamp_range)) <= :consensusEnd
                    ), fix_entity_stake as (
                      update entity_stake
                      set pending_reward = 0,
                          staked_node_id_start = -1
                      from staked_before_alive, last_26_file
                      where id = entity_id
                    ), fix_entity_history as (
                      update entity_history
                      set staked_node_id = -1,
                          stake_period_start = -1
                      from staked_before_alive, last_26_file
                      where id = entity_id
                    )
                    update entity
                    set staked_node_id = -1,
                        stake_period_start = -1
                    from staked_before_alive, last_26_file
                    where id = entity_id;
                    """;
    private static final MigrationVersion VERSION = MigrationVersion.fromVersion("1.68.3");

    private final ObjectProvider<NamedParameterJdbcOperations> jdbcOperationsProvider;
    private final ImporterProperties importerProperties;

    @Override
    public MigrationVersion getVersion() {
        return VERSION;
    }

    @Override
    public String getDescription() {
        return "Fix the staking information for accounts which configured staking before the feature is enabled";
    }

    @Override
    protected void doMigrate() {
        var hederaNetwork = importerProperties.getNetwork();
        if (!ImporterProperties.HederaNetwork.MAINNET.equalsIgnoreCase(hederaNetwork)) {
            return;
        }

        var stopwatch = Stopwatch.createStarted();
        long epochDay = Utility.getEpochDay(LAST_HAPI_26_RECORD_FILE_CONSENSUS_END_MAINNET);
        var params = new MapSqlParameterSource()
                .addValue("consensusEnd", LAST_HAPI_26_RECORD_FILE_CONSENSUS_END_MAINNET)
                .addValue("epochDay", epochDay);
        int count = jdbcOperationsProvider.getObject().update(MIGRATION_SQL, params);
        log.info("Fixed staking information for {} {} accounts in {}", count, hederaNetwork, stopwatch);
    }
}
