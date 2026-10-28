// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.common.domain.token.TokenAccountHistory;
import org.hiero.mirror.common.domain.token.TokenFreezeStatusEnum;
import org.hiero.mirror.common.domain.token.TokenKycStatusEnum;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.repository.TokenAccountHistoryRepository;
import org.hiero.mirror.importer.repository.TokenAccountRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.transaction.support.TransactionOperations;

@RequiredArgsConstructor
@Tag("migration")
class FixTreasuryChangeTokenAccountStatusMigrationTest extends ImporterIntegrationTest {

    private static final long CHANGE_TIMESTAMP = 10L;

    private final Environment environment;
    private final ImporterProperties importerProperties;
    private final FixTreasuryChangeTokenAccountStatusMigration migration;
    private final NamedParameterJdbcOperations namedParameterJdbcOperations;
    private final TokenAccountHistoryRepository tokenAccountHistoryRepository;
    private final TokenAccountRepository tokenAccountRepository;
    private final TransactionOperations transactionOperations;

    @Test
    void empty() {
        migration.doMigrate();
        assertThat(tokenAccountRepository.findAll()).isEmpty();
        assertThat(findHistory(TokenAccountHistory.class)).isEmpty();
    }

    @Test
    void migrate() {
        // given
        final var tokenId = treasuryChange(true);
        final var newTreasury = tokenAccount(tokenId, 5L, TokenFreezeStatusEnum.FROZEN, TokenKycStatusEnum.REVOKED);
        final var otherAccount =
                tokenAccount(tokenId, 5L, TokenFreezeStatusEnum.FROZEN, TokenKycStatusEnum.REVOKED, 9999L);

        // automatically associated new treasury
        final var autoAssociatedTokenId = treasuryChange(true);
        final var autoAssociated = tokenAccount(autoAssociatedTokenId, CHANGE_TIMESTAMP, null, null);

        // explicitly frozen and kyc revoked after the treasury change
        final var laterChangeTokenId = treasuryChange(true);
        final var laterChange = tokenAccount(
                laterChangeTokenId, CHANGE_TIMESTAMP + 5, TokenFreezeStatusEnum.FROZEN, TokenKycStatusEnum.REVOKED);

        // statuses not applicable. The statuses are fixed regardless of the token's keys, since readers show
        // NOT_APPLICABLE for a token without the key
        final var notApplicableTokenId = treasuryChange(true);
        final var notApplicable = tokenAccount(
                notApplicableTokenId, 5L, TokenFreezeStatusEnum.NOT_APPLICABLE, TokenKycStatusEnum.NOT_APPLICABLE);

        // new treasury already unfrozen and kyc granted
        final var alreadyFixedTokenId = treasuryChange(true);
        final var alreadyFixed =
                tokenAccount(alreadyFixedTokenId, 5L, TokenFreezeStatusEnum.UNFROZEN, TokenKycStatusEnum.GRANTED);

        // token updated without changing the treasury
        final var noChangeTokenId = treasuryChange(false);
        final var noChange =
                tokenAccount(noChangeTokenId, 5L, TokenFreezeStatusEnum.FROZEN, TokenKycStatusEnum.REVOKED);

        // when
        migration.doMigrate();

        // then
        // the snapshots prior to the treasury change are preserved in history
        final var expectedHistory = List.of(toHistory(newTreasury), toHistory(notApplicable));

        newTreasury.setFreezeStatus(TokenFreezeStatusEnum.UNFROZEN);
        newTreasury.setKycStatus(TokenKycStatusEnum.GRANTED);
        newTreasury.setTimestampRange(Range.atLeast(CHANGE_TIMESTAMP));
        // automatically associated at the treasury change, so there is no prior snapshot
        autoAssociated.setFreezeStatus(TokenFreezeStatusEnum.UNFROZEN);
        autoAssociated.setKycStatus(TokenKycStatusEnum.GRANTED);
        notApplicable.setFreezeStatus(TokenFreezeStatusEnum.UNFROZEN);
        notApplicable.setKycStatus(TokenKycStatusEnum.GRANTED);
        notApplicable.setTimestampRange(Range.atLeast(CHANGE_TIMESTAMP));
        assertThat(tokenAccountRepository.findAll())
                .containsExactlyInAnyOrder(
                        newTreasury, otherAccount, autoAssociated, laterChange, notApplicable, alreadyFixed, noChange);
        assertThat(findHistory(TokenAccountHistory.class)).containsExactlyInAnyOrderElementsOf(expectedHistory);
    }

    @Test
    void migrateWithHistory() {
        // given
        // frozen and kyc revoked prior to the treasury change, with the association in history
        final var tokenId = treasuryChange(true);
        final var associated = tokenAccount(tokenId, 2L, TokenFreezeStatusEnum.UNFROZEN, TokenKycStatusEnum.GRANTED);
        final var associatedHistory = toHistory(associated);
        associatedHistory.setTimestampRange(Range.closedOpen(2L, 5L));
        tokenAccountHistoryRepository.save(associatedHistory);
        associated.setCreatedTimestamp(2L);
        associated.setFreezeStatus(TokenFreezeStatusEnum.FROZEN);
        associated.setKycStatus(TokenKycStatusEnum.REVOKED);
        associated.setTimestampRange(Range.atLeast(5L));
        tokenAccountRepository.save(associated);

        // when
        migration.doMigrate();

        // then
        final var frozenHistory = toHistory(associated);
        associated.setFreezeStatus(TokenFreezeStatusEnum.UNFROZEN);
        associated.setKycStatus(TokenKycStatusEnum.GRANTED);
        associated.setTimestampRange(Range.atLeast(CHANGE_TIMESTAMP));
        assertThat(tokenAccountRepository.findAll()).containsExactly(associated);
        assertThat(findHistory(TokenAccountHistory.class)).containsExactlyInAnyOrder(associatedHistory, frozenHistory);
    }

    @Test
    @SuppressWarnings("unchecked")
    void migrateRollback() {
        // given
        final var tokenId = treasuryChange(true);
        final var newTreasury = tokenAccount(tokenId, 5L, TokenFreezeStatusEnum.FROZEN, TokenKycStatusEnum.REVOKED);

        // fails after the split and the freeze status update
        final var failingJdbcOperations = spy(namedParameterJdbcOperations);
        doThrow(new IllegalStateException("failure"))
                .when(failingJdbcOperations)
                .update(argThat((String sql) -> sql.contains("set kyc_status")), any(SqlParameterSource.class));
        final ObjectProvider<NamedParameterJdbcOperations> jdbcOperationsProvider = mock(ObjectProvider.class);
        when(jdbcOperationsProvider.getObject()).thenReturn(failingJdbcOperations);
        final ObjectProvider<TransactionOperations> transactionOperationsProvider = mock(ObjectProvider.class);
        when(transactionOperationsProvider.getObject()).thenReturn(transactionOperations);
        final var failingMigration = new FixTreasuryChangeTokenAccountStatusMigration(
                environment, importerProperties, jdbcOperationsProvider, transactionOperationsProvider);

        // when
        assertThatThrownBy(failingMigration::doMigrate).isInstanceOf(IllegalStateException.class);

        // then
        assertThat(tokenAccountRepository.findAll()).containsExactly(newTreasury);
        assertThat(findHistory(TokenAccountHistory.class)).isEmpty();

        // when
        migration.doMigrate();

        // then
        final var expectedHistory = toHistory(newTreasury);
        newTreasury.setFreezeStatus(TokenFreezeStatusEnum.UNFROZEN);
        newTreasury.setKycStatus(TokenKycStatusEnum.GRANTED);
        newTreasury.setTimestampRange(Range.atLeast(CHANGE_TIMESTAMP));
        assertThat(tokenAccountRepository.findAll()).containsExactly(newTreasury);
        assertThat(findHistory(TokenAccountHistory.class)).containsExactly(expectedHistory);
    }

    @Test
    void migrateDissociated() {
        // given
        final var tokenId = treasuryChange(true);
        final var dissociated = domainBuilder
                .tokenAccount()
                .customize(t -> t.accountId(treasury(tokenId))
                        .associated(false)
                        .createdTimestamp(2L)
                        .freezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .kycStatus(TokenKycStatusEnum.REVOKED)
                        .timestampRange(Range.atLeast(5L))
                        .tokenId(tokenId))
                .persist();

        // when
        migration.doMigrate();

        // then
        assertThat(tokenAccountRepository.findAll()).containsExactly(dissociated);
        assertThat(findHistory(TokenAccountHistory.class)).isEmpty();
    }

    @Test
    void migrateExceedsParameterLimit() {
        // given
        final var jdbcOperations = spy(namedParameterJdbcOperations);
        final var batchedMigration = new FixTreasuryChangeTokenAccountStatusMigration(
                environment, importerProperties, objectProvider(jdbcOperations), objectProvider(transactionOperations));
        final var tokenId = treasuryChange(true);
        final long firstTimestamp = CHANGE_TIMESTAMP + 5;
        final long lastTimestamp = firstTimestamp + 65_535;
        final var current = tokenAccount(tokenId, 5L, TokenFreezeStatusEnum.FROZEN, TokenKycStatusEnum.REVOKED);
        final var associatedHistory = toHistory(current);
        associatedHistory.setTimestampRange(Range.closedOpen(5L, firstTimestamp));
        tokenAccountHistoryRepository.save(associatedHistory);
        current.setTimestampRange(Range.atLeast(lastTimestamp));
        tokenAccountRepository.save(current);

        // Unchanged statuses require the transaction type to distinguish repeated freezes from a KYC revocation.
        // There are 65,536 subsequent snapshots, including the current row, exceeding the JDBC parameter limit.
        final var params = new MapSqlParameterSource()
                .addValue("accountId", current.getAccountId())
                .addValue("first", firstTimestamp)
                .addValue("freezeType", TransactionType.TOKENFREEZE.getProtoId())
                .addValue("last", lastTimestamp)
                .addValue("revokeType", TransactionType.TOKENREVOKEKYC.getProtoId())
                .addValue("success", ResponseCodeEnum.SUCCESS_VALUE)
                .addValue("tokenId", tokenId);
        namedParameterJdbcOperations.update("""
                insert into token_account_history (
                  account_id, associated, automatic_association, balance, balance_timestamp, created_timestamp,
                  freeze_status, kyc_status, timestamp_range, token_id
                )
                select account_id, associated, automatic_association, balance, balance_timestamp, created_timestamp,
                       freeze_status, kyc_status, int8range(ts, ts + 1), token_id
                from token_account cross join generate_series(:first, :last - 1) ts
                where account_id = :accountId and token_id = :tokenId
                """, params);
        namedParameterJdbcOperations.update("""
                insert into transaction (consensus_timestamp, payer_account_id, result, type, valid_start_ns)
                select ts, :accountId, :success, case when ts = :last then :revokeType else :freezeType end, ts
                from generate_series(:first, :last) ts
                """, params);

        // when
        batchedMigration.doMigrate();

        // then
        final var queries = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcOperations, atLeastOnce())
                .query(any(String.class), queries.capture(), any(RowCallbackHandler.class));
        assertThat(queries.getAllValues()).allSatisfy(query -> {
            final var timestamps = (Long[]) query.getValue("timestamps");
            assertThat(timestamps).isNotEmpty().hasSizeLessThanOrEqualTo(10_000);
            assertThat(query.getValue("minTimestamp")).isEqualTo(timestamps[0]);
            assertThat(query.getValue("maxTimestamp")).isEqualTo(timestamps[timestamps.length - 1]);
        });
        assertThat(tokenAccountRepository.findAll()).containsExactly(current);
        final var history = findHistory(TokenAccountHistory.class);
        assertThat(history).hasSize(65_537);
        assertThat(history)
                .filteredOn(t -> t.getTimestampLower() < firstTimestamp)
                .extracting(
                        TokenAccountHistory::getTimestampRange,
                        TokenAccountHistory::getFreezeStatus,
                        TokenAccountHistory::getKycStatus)
                .containsExactlyInAnyOrder(
                        tuple(
                                Range.closedOpen(5L, CHANGE_TIMESTAMP),
                                TokenFreezeStatusEnum.FROZEN,
                                TokenKycStatusEnum.REVOKED),
                        tuple(
                                Range.closedOpen(CHANGE_TIMESTAMP, firstTimestamp),
                                TokenFreezeStatusEnum.UNFROZEN,
                                TokenKycStatusEnum.GRANTED));
        assertThat(history)
                .filteredOn(t -> t.getTimestampLower() >= firstTimestamp)
                .hasSize(65_535)
                .allSatisfy(t -> {
                    assertThat(t.getFreezeStatus()).isEqualTo(TokenFreezeStatusEnum.FROZEN);
                    assertThat(t.getKycStatus()).isEqualTo(TokenKycStatusEnum.GRANTED);
                });

        // when run again
        batchedMigration.doMigrate();

        // then nothing changes
        assertThat(tokenAccountRepository.findAll()).containsExactly(current);
        assertThat(findHistory(TokenAccountHistory.class)).containsExactlyElementsOf(history);
    }

    @Test
    void migrateRepeatedTreasuryChanges() {
        // given
        // the treasury changes to the account at 10, away from it at 20, and back to it at 30
        final var tokenId = domainBuilder.id();
        final var account = EntityId.of(treasury(tokenId));
        final var other = domainBuilder.entityId();
        tokenVersion(tokenId, domainBuilder.entityId(), Range.closedOpen(1L, 10L));
        tokenVersion(tokenId, account, Range.closedOpen(10L, 20L));
        tokenVersion(tokenId, other, Range.closedOpen(20L, 30L));
        domainBuilder
                .token()
                .customize(t ->
                        t.timestampRange(Range.atLeast(30L)).tokenId(tokenId).treasuryAccountId(account))
                .persist();

        // associated at 5 and explicitly frozen at 25, while not the treasury
        domainBuilder
                .tokenAccountHistory()
                .customize(t -> t.accountId(account.getId())
                        .createdTimestamp(5L)
                        .freezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .kycStatus(TokenKycStatusEnum.REVOKED)
                        .timestampRange(Range.closedOpen(5L, 25L))
                        .tokenId(tokenId))
                .persist();
        final var current = domainBuilder
                .tokenAccount()
                .customize(t -> t.accountId(account.getId())
                        .createdTimestamp(5L)
                        .freezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .kycStatus(TokenKycStatusEnum.REVOKED)
                        .timestampRange(Range.atLeast(25L))
                        .tokenId(tokenId))
                .persist();
        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(25L).type(TransactionType.TOKENFREEZE.getProtoId()))
                .persist();

        // when
        migration.doMigrate();

        // then
        current.setFreezeStatus(TokenFreezeStatusEnum.UNFROZEN);
        current.setKycStatus(TokenKycStatusEnum.GRANTED);
        current.setTimestampRange(Range.atLeast(30L));
        assertThat(tokenAccountRepository.findAll()).containsExactly(current);
        assertThat(findHistory(TokenAccountHistory.class))
                .extracting(
                        TokenAccountHistory::getTimestampRange,
                        TokenAccountHistory::getFreezeStatus,
                        TokenAccountHistory::getKycStatus)
                .containsExactly(
                        tuple(Range.closedOpen(5L, 10L), TokenFreezeStatusEnum.FROZEN, TokenKycStatusEnum.REVOKED),
                        tuple(Range.closedOpen(10L, 25L), TokenFreezeStatusEnum.UNFROZEN, TokenKycStatusEnum.GRANTED),
                        // explicitly frozen at 25, kyc implicitly granted at 10 still applies
                        tuple(Range.closedOpen(25L, 30L), TokenFreezeStatusEnum.FROZEN, TokenKycStatusEnum.GRANTED));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(textBlock = """
            freeze,           TOKENFREEZE,     true,  5,  FROZEN,   REVOKED, FROZEN,   GRANTED
            unfreeze,         TOKENUNFREEZE,   true,  5,  UNFROZEN, REVOKED, UNFROZEN, GRANTED
            revoke kyc,       TOKENREVOKEKYC,  true,  5,  FROZEN,   REVOKED, UNFROZEN, REVOKED
            grant kyc,        TOKENGRANTKYC,   true,  5,  FROZEN,   GRANTED, UNFROZEN, GRANTED
            unknown,          ,                true,  5,  FROZEN,   REVOKED, FROZEN,   REVOKED
            unknown freeze,   ,                true,  5,  UNFROZEN, REVOKED, UNFROZEN, GRANTED
            dissociated,      TOKENDISSOCIATE, false, 5,  FROZEN,   REVOKED, FROZEN,   REVOKED
            associated again, TOKENASSOCIATE,  true,  15, FROZEN,   REVOKED, FROZEN,   REVOKED
            """)
    void migrateSubsequentChange(
            String name,
            TransactionType type,
            boolean associated,
            long createdTimestamp,
            TokenFreezeStatusEnum freezeStatus,
            TokenKycStatusEnum kycStatus,
            TokenFreezeStatusEnum expectedFreezeStatus,
            TokenKycStatusEnum expectedKycStatus) {
        // given
        // associated at 5, treasury changed at 10, and a subsequent change at 15
        final var tokenId = treasuryChange(true);
        final long accountId = treasury(tokenId);
        final long subsequentTimestamp = CHANGE_TIMESTAMP + 5;
        final var associatedHistory = domainBuilder
                .tokenAccountHistory()
                .customize(t -> t.accountId(accountId)
                        .createdTimestamp(5L)
                        .freezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .kycStatus(TokenKycStatusEnum.REVOKED)
                        .timestampRange(Range.closedOpen(5L, subsequentTimestamp))
                        .tokenId(tokenId))
                .persist();
        final var current = domainBuilder
                .tokenAccount()
                .customize(t -> t.accountId(accountId)
                        .associated(associated)
                        .createdTimestamp(createdTimestamp)
                        .freezeStatus(freezeStatus)
                        .kycStatus(kycStatus)
                        .timestampRange(Range.atLeast(subsequentTimestamp))
                        .tokenId(tokenId))
                .persist();
        if (type != null) {
            domainBuilder
                    .transaction()
                    .customize(t -> t.consensusTimestamp(subsequentTimestamp).type(type.getProtoId()))
                    .persist();
        }

        // when
        migration.doMigrate();

        // then
        current.setFreezeStatus(expectedFreezeStatus);
        current.setKycStatus(expectedKycStatus);
        assertThat(tokenAccountRepository.findAll()).containsExactly(current);
        assertThat(findHistory(TokenAccountHistory.class))
                .extracting(
                        TokenAccountHistory::getTimestampRange,
                        TokenAccountHistory::getFreezeStatus,
                        TokenAccountHistory::getKycStatus)
                .containsExactly(
                        tuple(
                                associatedHistory.getTimestampRange().intersection(Range.lessThan(CHANGE_TIMESTAMP)),
                                TokenFreezeStatusEnum.FROZEN,
                                TokenKycStatusEnum.REVOKED),
                        tuple(
                                Range.closedOpen(CHANGE_TIMESTAMP, subsequentTimestamp),
                                TokenFreezeStatusEnum.UNFROZEN,
                                TokenKycStatusEnum.GRANTED));
    }

    private TokenAccountHistory toHistory(TokenAccount tokenAccount) {
        return TokenAccountHistory.builder()
                .accountId(tokenAccount.getAccountId())
                .associated(tokenAccount.getAssociated())
                .automaticAssociation(tokenAccount.getAutomaticAssociation())
                .balance(tokenAccount.getBalance())
                .balanceTimestamp(tokenAccount.getBalanceTimestamp())
                .createdTimestamp(tokenAccount.getCreatedTimestamp())
                .freezeStatus(tokenAccount.getFreezeStatus())
                .kycStatus(tokenAccount.getKycStatus())
                .timestampRange(
                        Range.closedOpen(tokenAccount.getTimestampRange().lowerEndpoint(), CHANGE_TIMESTAMP))
                .tokenId(tokenAccount.getTokenId())
                .build();
    }

    private TokenAccount tokenAccount(
            long tokenId, long timestamp, TokenFreezeStatusEnum freezeStatus, TokenKycStatusEnum kycStatus) {
        return tokenAccount(tokenId, timestamp, freezeStatus, kycStatus, treasury(tokenId));
    }

    private TokenAccount tokenAccount(
            long tokenId,
            long timestamp,
            TokenFreezeStatusEnum freezeStatus,
            TokenKycStatusEnum kycStatus,
            long accountId) {
        return domainBuilder
                .tokenAccount()
                .customize(t -> t.accountId(accountId)
                        .createdTimestamp(timestamp)
                        .freezeStatus(freezeStatus)
                        .kycStatus(kycStatus)
                        .timestampRange(Range.atLeast(timestamp))
                        .tokenId(tokenId))
                .persist();
    }

    private void tokenVersion(long tokenId, EntityId treasury, Range<Long> timestampRange) {
        domainBuilder
                .tokenHistory()
                .customize(
                        t -> t.timestampRange(timestampRange).tokenId(tokenId).treasuryAccountId(treasury))
                .persist();
    }

    /**
     * Persists a token with a history version prior to CHANGE_TIMESTAMP, and a current version starting at
     * CHANGE_TIMESTAMP with the treasury changed if requested.
     */
    private long treasuryChange(boolean changeTreasury) {
        final var tokenId = domainBuilder.id();
        final var newTreasury = EntityId.of(treasury(tokenId));
        final var previousTreasury = changeTreasury ? domainBuilder.entityId() : newTreasury;
        domainBuilder
                .tokenHistory()
                .customize(t -> t.timestampRange(Range.closedOpen(1L, CHANGE_TIMESTAMP))
                        .tokenId(tokenId)
                        .treasuryAccountId(previousTreasury))
                .persist();
        domainBuilder
                .token()
                .customize(t -> t.timestampRange(Range.atLeast(CHANGE_TIMESTAMP))
                        .tokenId(tokenId)
                        .treasuryAccountId(newTreasury))
                .persist();
        return tokenId;
    }

    private static long treasury(long tokenId) {
        return tokenId + 100_000L;
    }
}
