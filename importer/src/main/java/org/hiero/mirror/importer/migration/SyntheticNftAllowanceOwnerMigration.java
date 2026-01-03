// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import com.google.common.base.Stopwatch;
import jakarta.inject.Named;
import java.util.concurrent.atomic.AtomicBoolean;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.config.Owner;
import org.hiero.mirror.importer.exception.ImporterException;
import org.hiero.mirror.importer.parser.record.RecordStreamFileListener;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.util.Version;
import org.springframework.jdbc.core.JdbcOperations;

@Named
public class SyntheticNftAllowanceOwnerMigration extends RepeatableMigration implements RecordStreamFileListener {

    static final Version HAPI_VERSION_0_37_0 = new Version(0, 37, 0);
    private final AtomicBoolean executed = new AtomicBoolean(false);

    private static final String UPDATE_NFT_ALLOWANCE_OWNER_SQL = """
            begin;

            create temp table nft_allowance_temp (
              approved_for_all  boolean not null,
              created_timestamp bigint  not null,
              owner             bigint  not null,
              payer_account_id  bigint  not null,
              spender           bigint  not null,
              token_id          bigint  not null,
              primary key (owner, spender, token_id, created_timestamp)
            ) on commit drop;

            with affected as (
              select nfta.*, cr.consensus_timestamp, cr.sender_id
              from (
                select * from nft_allowance
                union all
                select * from nft_allowance_history
              ) nfta
              join contract_result cr on cr.consensus_timestamp = lower(nfta.timestamp_range)
              where cr.sender_id is not null
            ), delete_nft_allowance as (
              delete from nft_allowance nfta
              using affected a
              where nfta.owner in (a.owner, a.sender_id) and nfta.spender = a.spender and nfta.token_id = a.token_id
              returning
                nfta.approved_for_all,
                lower(nfta.timestamp_range) as created_timestamp,
                a.sender_id as owner,
                nfta.payer_account_id,
                nfta.spender,
                nfta.token_id
            ), delete_nft_allowance_history as (
              delete from nft_allowance_history nfta
              using affected a
              where (nfta.owner = a.owner and nfta.spender = a.spender and nfta.token_id = a.token_id and nfta.timestamp_range = a.timestamp_range) or
                (nfta.owner = a.sender_id and nfta.spender = a.spender and nfta.token_id = a.token_id)
              returning
                nfta.approved_for_all,
                lower(nfta.timestamp_range) as created_timestamp,
                a.sender_id as owner,
                nfta.payer_account_id,
                nfta.spender,
                nfta.token_id
            )
            insert into nft_allowance_temp (approved_for_all, created_timestamp, owner, payer_account_id, spender, token_id)
            select approved_for_all, created_timestamp, owner, payer_account_id, spender, token_id from delete_nft_allowance
            union all
            select approved_for_all, created_timestamp, owner, payer_account_id, spender, token_id from delete_nft_allowance_history;

            with correct_timestamp_range as (
              select
                approved_for_all,
                owner,
                payer_account_id,
                spender,
                int8range(created_timestamp, (
                  select c.created_timestamp
                  from nft_allowance_temp c
                  where c.owner = p.owner and c.spender = p.spender and c.token_id = p.token_id
                    and c.created_timestamp > p.created_timestamp
                  order by c.created_timestamp
                  limit 1)) as timestamp_range,
                token_id
              from nft_allowance_temp p
            ), history as (
              insert into nft_allowance_history (approved_for_all, owner, payer_account_id, spender, timestamp_range, token_id)
              select * from correct_timestamp_range where upper(timestamp_range) is not null
            )
            insert into nft_allowance (approved_for_all, owner, payer_account_id, spender, timestamp_range, token_id)
            select * from correct_timestamp_range where upper(timestamp_range) is null;

            commit;
            """;

    private final ObjectProvider<JdbcOperations> jdbcOperationsProvider;
    private final ObjectProvider<RecordFileRepository> recordFileRepositoryProvider;

    public SyntheticNftAllowanceOwnerMigration(
            @Owner ObjectProvider<JdbcOperations> jdbcOperationsProvider,
            ImporterProperties importerProperties,
            ObjectProvider<RecordFileRepository> recordFileRepositoryProvider) {
        super(importerProperties.getMigration());
        this.jdbcOperationsProvider = jdbcOperationsProvider;
        this.recordFileRepositoryProvider = recordFileRepositoryProvider;
    }

    @Override
    public String getDescription() {
        return "Updates the owner for synthetic nft allowances to the corresponding contract result sender";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        // The version where contract_result sender_id was added
        return MigrationVersion.fromVersion("1.58.4");
    }

    @Override
    protected void doMigrate() {
        var stopwatch = Stopwatch.createStarted();
        jdbcOperationsProvider.getObject().execute(UPDATE_NFT_ALLOWANCE_OWNER_SQL);
        log.info("Updated nft allowance owners in {}", stopwatch);
    }

    @Override
    public void onEnd(RecordFile streamFile) throws ImporterException {
        if (streamFile == null) {
            return;
        }

        // The services version 0.37.0 has the fixes this migration solves.
        if (streamFile.getHapiVersion().isGreaterThanOrEqualTo(HAPI_VERSION_0_37_0)
                && executed.compareAndSet(false, true)) {
            var latestFile = recordFileRepositoryProvider.getObject().findLatestBefore(streamFile.getConsensusStart());
            if (latestFile
                    .filter(f -> f.getHapiVersion().isLessThan(HAPI_VERSION_0_37_0))
                    .isPresent()) {
                doMigrate();
            }
        }
    }
}
