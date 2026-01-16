// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.springframework.data.util.Predicates.negate;

import com.google.common.base.Stopwatch;
import jakarta.inject.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.BooleanUtils;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.common.domain.balance.TokenBalance;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.AbstractTokenAccount;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.db.TimePartitionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.transaction.support.TransactionTemplate;

@Named
class FixAirdropTokenAssociationMigration extends ConfigurableJavaMigration {

    private static final String ACCOUNT_ID = "accountId";
    private static final String ASSOCIATED = "associated";
    private static final String BALANCE = "balance";
    private static final String BALANCE_TIMESTAMP = "balanceTimestamp";
    private static final String CREATED_TIMESTAMP = "createdTimestamp";
    private static final String FROM = "from";
    private static final String TO = "to";
    private static final String TOKEN_ID = "tokenId";
    private static final String TIMESTAMP = "timestamp";

    private static final String GET_BALANCE_SNAPSHOT_TIMESTAMPS_SQL = """
            select consensus_timestamp
            from account_balance
            where account_id = 2 and consensus_timestamp >= :timestamp
            order by consensus_timestamp
            """;
    private static final String GET_CLAIMED_AIRDROPS_SQL = """
            select
              receiver_account_id as account_id,
              lower(timestamp_range) as consensus_timestamp,
              (amount is not null) as fungible,
              token_id
            from (
              (
                select amount, receiver_account_id, token_id, timestamp_range
                from token_airdrop
                where state = 'CLAIMED'
              )
              union all
              (
                select amount, receiver_account_id, token_id, timestamp_range
                from token_airdrop_history
                where state = 'CLAIMED'
              )
            ) as all_claimed
            group by account_id, amount, consensus_timestamp, token_id
            order by consensus_timestamp
            """;
    private static final String GET_FUNGIBLE_TOKEN_BALANCE_CHANGE_SQL = """
            select sum(amount) as change, max(consensus_timestamp) as consensus_timestamp
            from token_transfer
            where account_id = :accountId
              and token_id = :tokenId
              and consensus_timestamp > :from
              and consensus_timestamp <= :to
            group by account_id, token_id
            """;
    private static final String GET_TOKEN_ACCOUNT_VALID_TO_TIMESTAMP_SQL = """
            select consensus_timestamp - 1
            from (
              (
                select lower(timestamp_range) as consensus_timestamp
                from token_account
                where account_id = :accountId
                  and associated is false
                  and lower(timestamp_range) >:timestamp
                  and token_id = :tokenId
                order by lower(timestamp_range)
                limit 1
              )
              union all
              (
                select lower(timestamp_range) as consensus_timestamp
                from token_account_history
                where account_id = :accountId
                  and associated is false
                  and lower(timestamp_range) >:timestamp
                  and token_id = :tokenId
                order by lower(timestamp_range)
                limit 1
              )
            ) as token_dissociate
            order by consensus_timestamp
            limit 1
            """;
    private static final String GET_NFT_TRANSFERS_SQL = """
            with nft_transfer as (
              select consensus_timestamp, jsonb_array_elements(nft_transfer) as transfer
              from transaction
              where nft_transfer is not null
                and consensus_timestamp > :from
                and consensus_timestamp <= :to
              order by consensus_timestamp
            )
            select
              consensus_timestamp,
              transfer->>'token_id' as token_id,
              transfer->>'receiver_account_id' as receiver_account_id,
              transfer->>'sender_account_id' as sender_account_id,
              transfer->>'serial_number' as serial_number
            from nft_transfer
            order by consensus_timestamp
            """;
    private static final String INSERT_TOKEN_BALANCE_SQL = """
            insert into token_balance (account_id, balance, consensus_timestamp, token_id)
            values (?, ?, ?, ?)
            on conflict do nothing
            """;
    private static final String IS_TOKEN_ACCOUNT_MISSING_SQL = """
            select not exists((
              select * from token_account
              where account_id = :accountId
                and associated is true
                and token_id = :tokenId
                and timestamp_range @> :timestamp
            )
            union all
            (
              select * from token_account_history
              where account_id = :accountId
                and associated is true
                and token_id = :tokenId
                and timestamp_range @> :timestamp
            ))
            """;
    // Patches token_account and token_account_history tables with the missing token account association, handles cases
    // that if a previous token_account(_history) row needs to be adjusted / moved, and / or the new row needs to be
    // adjusted then upserted to token_account or appended to token_account_history
    private static final String PATCH_TOKEN_ACCOUNT_SQL = """
            with token_account as (
              select *
              from (
                (
                  select *
                  from token_account
                  where account_id = :accountId and token_id = :tokenId
                )
                union all
                (
                  select *
                  from token_account_history
                  where account_id = :accountId and token_id = :tokenId
                )
              ) as all_token_account
              order by lower(timestamp_range)
            ), previous as (
              select *
              from token_account
              where lower(timestamp_range) < :timestamp
              order by lower(timestamp_range) desc
              limit 1
            ), next as (
              select *
              from token_account
              where lower(timestamp_range) > :timestamp
              order by lower(timestamp_range)
              limit 1
            ), update_previous as (
              update token_account_history as th
              set timestamp_range = int8range(lower(th.timestamp_range), :timestamp)
              from previous as p
              where upper(p.timestamp_range) is not null
                and th.account_id = p.account_id
                and th.token_id = p.token_id
                and lower(th.timestamp_range) = lower(p.timestamp_range)
            ), append_previous as (
              insert into token_account_history (
                account_id,
                associated,
                automatic_association,
                balance,
                balance_timestamp,
                created_timestamp,
                freeze_status,
                kyc_status,
                timestamp_range,
                token_id
              )
              select
                account_id,
                associated,
                automatic_association,
                balance,
                balance_timestamp,
                created_timestamp,
                freeze_status,
                kyc_status,
                int8range(lower(timestamp_range), :timestamp),
                token_id
              from previous
              where upper(timestamp_range) is null
            ), append_current as (
              insert into token_account_history (
                account_id,
                associated,
                automatic_association,
                balance,
                balance_timestamp,
                created_timestamp,
                timestamp_range,
                token_id
              )
              select
                :accountId,
                :associated,
                false,
                :balance,
                :balanceTimestamp,
                :createdTimestamp,
                int8range(:timestamp, (select lower(timestamp_range) from next limit 1)),
                :tokenId
              where exists(select * from next)
            )
            insert into token_account (
              account_id,
              associated,
              automatic_association,
              balance,
              balance_timestamp,
              created_timestamp,
              freeze_status,
              kyc_status,
              timestamp_range,
              token_id
            )
            select
              :accountId,
              :associated,
              false,
              :balance,
              :balanceTimestamp,
              :createdTimestamp,
              null,
              null,
              int8range(:timestamp, null),
              :tokenId
            where not exists(select * from next)
            on conflict (account_id, token_id) do update
            set
              associated = excluded.associated,
              automatic_association = excluded.automatic_association,
              balance = excluded.balance,
              balance_timestamp = excluded.balance_timestamp,
              created_timestamp = excluded.created_timestamp,
              freeze_status = excluded.freeze_status,
              kyc_status = excluded.kyc_status,
              timestamp_range = excluded.timestamp_range;
            """;
    private static final RowMapper<ClaimedAirdrop> CLAIMED_AIRDROP_ROW_MAPPER =
            new DataClassRowMapper<>(ClaimedAirdrop.class);
    private static final RowMapper<NftTransfer> NFT_TRANSFER_ROW_MAPPER = new DataClassRowMapper<>(NftTransfer.class);
    private static final RowMapper<TokenBalanceChange> TOKEN_BALANCE_CHANGE_ROW_MAPPER =
            new DataClassRowMapper<>(TokenBalanceChange.class);

    private final ObjectProvider<NamedParameterJdbcOperations> jdbcOperationsProvider;
    private final ObjectProvider<TimePartitionService> timePartitionServiceProvider;
    private final ObjectProvider<TransactionTemplate> transactionTemplateProvider;
    private final boolean v2;

    FixAirdropTokenAssociationMigration(
            Environment environment,
            ImporterProperties importerProperties,
            ObjectProvider<NamedParameterJdbcOperations> jdbcOperationsProvider,
            ObjectProvider<TimePartitionService> timePartitionServiceProvider,
            ObjectProvider<TransactionTemplate> transactionTemplateProvider) {
        super(importerProperties.getMigration());
        this.jdbcOperationsProvider = jdbcOperationsProvider;
        this.timePartitionServiceProvider = timePartitionServiceProvider;
        this.transactionTemplateProvider = transactionTemplateProvider;
        this.v2 = environment.acceptsProfiles(Profiles.of("v2"));
    }

    @Override
    @SuppressWarnings("java:S3776")
    protected void doMigrate() throws IOException {
        transactionTemplateProvider.getObject().executeWithoutResult(status -> {
            var stopwatch = Stopwatch.createStarted();
            var claimedAirdrops = getClaimedAirdrops();
            if (claimedAirdrops.isEmpty()) {
                log.info("There are no claimed airdrops with missing token associations");
                return;
            }

            var missingTokenAccounts = getMissingTokenAccounts(claimedAirdrops);
            if (missingTokenAccounts.isEmpty()) {
                log.info("No missing token accounts");
                return;
            }

            long firstTokenAccountTimestamp =
                    missingTokenAccounts.getFirst().getTokenAccount().getCreatedTimestamp();
            var params = new MapSqlParameterSource(
                    TIMESTAMP, claimedAirdrops.getFirst().getConsensusTimestamp());
            var balanceSnapshotTimestamps = jdbcOperationsProvider
                    .getObject()
                    .queryForList(GET_BALANCE_SNAPSHOT_TIMESTAMPS_SQL, params, Long.class);
            // Add max long as a sentinel value so the iterating logic can also work for partial mirrornode where
            // account 2 may be missing thus no balance snapshot timestamp at all
            balanceSnapshotTimestamps.add(Long.MAX_VALUE);

            // Previous snapshot timestamp is exclusive and current balance snapshot timestamp is inclusive
            long previousSnapshotTimestamp = firstTokenAccountTimestamp - 1;
            int processed = 0;
            long lastElapsed = 0;

            for (long snapshotTimestamp : balanceSnapshotTimestamps) {
                var activeTokenAccounts =
                        getActiveTokenAccounts(missingTokenAccounts, previousSnapshotTimestamp, snapshotTimestamp);
                boolean mayNeedNftTransfer =
                        activeTokenAccounts.stream().anyMatch(negate(TokenAccountMeta::isFungible));
                var nftTransfers = mayNeedNftTransfer
                        ? getNftTransfers(previousSnapshotTimestamp, snapshotTimestamp)
                        : Collections.<NftTransfer>emptyList();
                boolean fullSnapshot = isCurrentSnapshotFull(previousSnapshotTimestamp, snapshotTimestamp);
                var tokenBalanceSnapshot = new ArrayList<TokenBalance>();

                for (var tokenAccountMeta : activeTokenAccounts) {
                    updateTokenAccountBalance(
                            tokenAccountMeta, nftTransfers, previousSnapshotTimestamp, snapshotTimestamp);

                    var tokenAccount = tokenAccountMeta.getTokenAccount();
                    if (snapshotTimestamp != Long.MAX_VALUE
                            && tokenAccountMeta.getValidToTimestamp() >= snapshotTimestamp
                            && (fullSnapshot || tokenAccount.getBalanceTimestamp() > previousSnapshotTimestamp)) {
                        var id = new TokenBalance.Id(
                                snapshotTimestamp,
                                EntityId.of(tokenAccount.getAccountId()),
                                EntityId.of(tokenAccount.getTokenId()));
                        var tokenBalance = TokenBalance.builder()
                                .balance(tokenAccount.getBalance())
                                .id(id)
                                .build();
                        tokenBalanceSnapshot.add(tokenBalance);
                    }
                }

                persistTokenBalanceSnapshot(tokenBalanceSnapshot);
                previousSnapshotTimestamp = snapshotTimestamp;
                processed++;

                long elapsed = stopwatch.elapsed(TimeUnit.SECONDS);
                if (elapsed - lastElapsed >= 10) {
                    log.info(
                            "{}/{} - Completed balance snapshot {}, processed {} active token accounts",
                            processed,
                            balanceSnapshotTimestamps.size(),
                            snapshotTimestamp,
                            activeTokenAccounts.size());
                    lastElapsed = elapsed;
                }
            }

            persistTokenAccounts(missingTokenAccounts);
            log.info("Fixed {} token accounts for claimed airdrops in {}", missingTokenAccounts.size(), stopwatch);
        });
    }

    @Override
    public String getDescription() {
        return "Fix missing token association when processing TokenClaimAirdrop transaction";
    }

    @Override
    public MigrationVersion getVersion() {
        return v2 ? MigrationVersion.fromVersion("2.6.0") : MigrationVersion.fromVersion("1.101.0");
    }

    private boolean isCurrentSnapshotFull(long previousTimestamp, long currentTimestamp) {
        return timePartitionServiceProvider
                        .getObject()
                        .getOverlappingTimePartitions("account_balance", previousTimestamp, currentTimestamp)
                        .size()
                > 1;
    }

    /**
     * Gets active token accounts given the timestamp range (from, to]
     * @param tokenAccounts - All token accounts with metadata
     * @param fromTimestamp - The from timestamp, exclusive
     * @param toTimestamp - the to timestamp, inclusive
     * @return List of active token accounts, ordered by created timestamp
     */
    private List<TokenAccountMeta> getActiveTokenAccounts(
            List<TokenAccountMeta> tokenAccounts, long fromTimestamp, long toTimestamp) {
        return tokenAccounts.stream()
                .filter(tokenAccountMeta -> {
                    long createdTimestamp = tokenAccountMeta.getTokenAccount().getCreatedTimestamp();
                    long validToTimestamp = tokenAccountMeta.getValidToTimestamp();
                    return createdTimestamp <= toTimestamp && validToTimestamp > fromTimestamp;
                })
                .toList();
    }

    /**
     * Gets the claimed airdrops with missing token account association
     * @return Claimed airdrops with missing token account association, ordered by when it's claimed
     */
    private List<ClaimedAirdrop> getClaimedAirdrops() {
        return jdbcOperationsProvider
                .getObject()
                .getJdbcOperations()
                .queryForStream(GET_CLAIMED_AIRDROPS_SQL, CLAIMED_AIRDROP_ROW_MAPPER)
                .filter(claimedAirdrop -> {
                    var params = new MapSqlParameterSource()
                            .addValue(ACCOUNT_ID, claimedAirdrop.getAccountId())
                            .addValue(TOKEN_ID, claimedAirdrop.getTokenId())
                            .addValue(TIMESTAMP, claimedAirdrop.getConsensusTimestamp());
                    return BooleanUtils.isTrue(jdbcOperationsProvider
                            .getObject()
                            .queryForObject(IS_TOKEN_ACCOUNT_MISSING_SQL, params, Boolean.class));
                })
                .toList();
    }

    private TokenBalanceChange getFungibleTokenBalanceChange(
            TokenAccountMeta tokenAccountMeta, long fromTimestamp, long toTimestamp) {
        var tokenAccount = tokenAccountMeta.getTokenAccount();
        // From timestamp is exclusive so deduct 1ns from token account's associated timestamp
        fromTimestamp = Math.max(fromTimestamp, tokenAccount.getCreatedTimestamp() - 1);
        toTimestamp = Math.min(toTimestamp, tokenAccountMeta.getValidToTimestamp());
        var params = new MapSqlParameterSource()
                .addValue(ACCOUNT_ID, tokenAccount.getAccountId())
                .addValue(TOKEN_ID, tokenAccount.getTokenId())
                .addValue(FROM, fromTimestamp)
                .addValue(TO, toTimestamp);

        try {
            return Objects.requireNonNull(jdbcOperationsProvider
                    .getObject()
                    .queryForObject(GET_FUNGIBLE_TOKEN_BALANCE_CHANGE_SQL, params, TOKEN_BALANCE_CHANGE_ROW_MAPPER));
        } catch (IncorrectResultSizeDataAccessException ex) {
            // ignore
            return null;
        }
    }

    /**
     * Gets the missing token accounts from the claimed airdrops. Note for the same token and account pair, the function
     * will discard subsequent token accounts if there is no token dissociate in between
     *
     * @param claimedAirdrops - Claimed airdrops with missing token account association, ordered by when it's claimed
     * @return List of missing token accounts, ordered by created timestamp
     */
    private List<TokenAccountMeta> getMissingTokenAccounts(List<ClaimedAirdrop> claimedAirdrops) {
        var missingTokenAccounts = new ArrayList<TokenAccountMeta>();
        var tokenAccountState = new HashMap<AbstractTokenAccount.Id, TokenAccountMeta>();

        for (var claimedAirdrop : claimedAirdrops) {
            var id = new AbstractTokenAccount.Id();
            id.setAccountId(claimedAirdrop.getAccountId());
            id.setTokenId(claimedAirdrop.getTokenId());

            if (Optional.ofNullable(tokenAccountState.get(id))
                    .map(TokenAccountMeta::isAlwaysValid)
                    .orElse(false)) {
                continue;
            }

            var tokenAccount = new TokenAccount();
            long createdTimestamp = claimedAirdrop.getConsensusTimestamp();
            tokenAccount.setAccountId(id.getAccountId());
            tokenAccount.setAssociated(true);
            tokenAccount.setAutomaticAssociation(false);
            tokenAccount.setBalance(0);
            tokenAccount.setBalanceTimestamp(createdTimestamp);
            tokenAccount.setCreatedTimestamp(createdTimestamp);
            tokenAccount.setTimestampLower(createdTimestamp);
            tokenAccount.setTokenId(id.getTokenId());

            long validToTimestamp = Long.MAX_VALUE;
            try {
                var params = new MapSqlParameterSource()
                        .addValue(ACCOUNT_ID, id.getAccountId())
                        .addValue(TOKEN_ID, id.getTokenId())
                        .addValue(TIMESTAMP, createdTimestamp);
                validToTimestamp = Objects.requireNonNull(jdbcOperationsProvider
                        .getObject()
                        .queryForObject(GET_TOKEN_ACCOUNT_VALID_TO_TIMESTAMP_SQL, params, Long.class));
            } catch (IncorrectResultSizeDataAccessException ex) {
                // ignore
            }

            var tokenAccountMeta = TokenAccountMeta.builder()
                    .fungible(claimedAirdrop.isFungible())
                    .tokenAccount(tokenAccount)
                    .validToTimestamp(validToTimestamp)
                    .build();
            missingTokenAccounts.add(tokenAccountMeta);
            tokenAccountState.put(id, tokenAccountMeta);
        }

        return missingTokenAccounts;
    }

    private TokenBalanceChange getNftBalanceChange(List<NftTransfer> nftTransfers, TokenAccountMeta tokenAccountMeta) {
        var tokenAccount = tokenAccountMeta.getTokenAccount();
        var accountId = (Long) tokenAccount.getAccountId();
        long createdTimestamp = tokenAccount.getCreatedTimestamp();
        long validToTimestamp = tokenAccountMeta.getValidToTimestamp();

        long change = 0;
        Long lastTimestamp = null;

        for (var nftTransfer : nftTransfers) {
            long consensusTimestamp = nftTransfer.getConsensusTimestamp();
            if (consensusTimestamp >= createdTimestamp) {
                if (consensusTimestamp > validToTimestamp) {
                    break;
                }

                if (nftTransfer.getTokenId() == tokenAccount.getTokenId()) {
                    if (Objects.equals(nftTransfer.getReceiverAccountId(), accountId)) {
                        change++;
                        lastTimestamp = consensusTimestamp;
                    } else if (Objects.equals(nftTransfer.getSenderAccountId(), accountId)) {
                        change--;
                        lastTimestamp = consensusTimestamp;
                    }
                }
            }
        }

        return lastTimestamp != null
                ? TokenBalanceChange.builder()
                        .change(change)
                        .consensusTimestamp(lastTimestamp)
                        .build()
                : null;
    }

    private List<NftTransfer> getNftTransfers(long fromTimestamp, long toTimestamp) {
        var params = new MapSqlParameterSource().addValue(FROM, fromTimestamp).addValue(TO, toTimestamp);
        return jdbcOperationsProvider.getObject().query(GET_NFT_TRANSFERS_SQL, params, NFT_TRANSFER_ROW_MAPPER);
    }

    private void persistTokenAccounts(List<TokenAccountMeta> tokenAccountMetas) {
        var batchParams = tokenAccountMetas.stream()
                .map(TokenAccountMeta::getTokenAccount)
                .map(ta -> new MapSqlParameterSource()
                        .addValue(ACCOUNT_ID, ta.getAccountId())
                        .addValue(ASSOCIATED, ta.getAssociated())
                        .addValue(BALANCE, ta.getBalance())
                        .addValue(BALANCE_TIMESTAMP, ta.getBalanceTimestamp())
                        .addValue(CREATED_TIMESTAMP, ta.getCreatedTimestamp())
                        .addValue(TIMESTAMP, ta.getTimestampLower())
                        .addValue(TOKEN_ID, ta.getTokenId()))
                .toArray(SqlParameterSource[]::new);
        jdbcOperationsProvider.getObject().batchUpdate(PATCH_TOKEN_ACCOUNT_SQL, batchParams);
    }

    private void persistTokenBalanceSnapshot(Collection<TokenBalance> tokeBalances) {
        jdbcOperationsProvider
                .getObject()
                .getJdbcOperations()
                .batchUpdate(INSERT_TOKEN_BALANCE_SQL, tokeBalances, tokeBalances.size(), (ps, tokenBalance) -> {
                    var id = Objects.requireNonNull(tokenBalance.getId());
                    ps.setLong(1, id.getAccountId().getId());
                    ps.setLong(2, tokenBalance.getBalance());
                    ps.setLong(3, id.getConsensusTimestamp());
                    ps.setLong(4, id.getTokenId().getId());
                });
    }

    private void updateTokenAccountBalance(
            TokenAccountMeta tokenAccountMeta, List<NftTransfer> nftTransfers, long fromTimestamp, long toTimestamp) {
        var balanceChange = tokenAccountMeta.isFungible()
                ? getFungibleTokenBalanceChange(tokenAccountMeta, fromTimestamp, toTimestamp)
                : getNftBalanceChange(nftTransfers, tokenAccountMeta);
        if (balanceChange != null) {
            var tokenAccount = tokenAccountMeta.getTokenAccount();
            tokenAccount.setBalance(tokenAccount.getBalance() + balanceChange.getChange());
            tokenAccount.setBalanceTimestamp(balanceChange.getConsensusTimestamp());
        }
    }

    @Data
    private static class ClaimedAirdrop {
        private long accountId;
        private long consensusTimestamp;
        private boolean fungible;
        private long tokenId;
    }

    @Data
    private static class NftTransfer {
        private long consensusTimestamp;
        private Long receiverAccountId;
        private Long senderAccountId;
        private long serialNumber;
        private long tokenId;
    }

    @Builder
    @Data
    private static class TokenAccountMeta {
        private boolean fungible;
        private TokenAccount tokenAccount;
        private long validToTimestamp;

        private boolean isAlwaysValid() {
            return validToTimestamp == Long.MAX_VALUE;
        }
    }

    @Builder
    @Data
    private static class TokenBalanceChange {
        private long change;
        private long consensusTimestamp;
    }
}
