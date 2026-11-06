// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.FixedFee;
import org.hiero.mirror.common.domain.token.FractionalFee;
import org.hiero.mirror.common.domain.token.Token;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.common.domain.token.TokenAccountHistory;
import org.hiero.mirror.common.domain.token.TokenFreezeStatusEnum;
import org.hiero.mirror.common.domain.token.TokenHistory;
import org.hiero.mirror.common.domain.token.TokenKycStatusEnum;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.hiero.mirror.importer.repository.TokenAccountRepository;
import org.hiero.mirror.importer.repository.TokenRepository;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@RequiredArgsConstructor
@Tag("migration")
class FixFeeCollectorTokenAccountStatusMigrationTest extends ImporterIntegrationTest {

    private static final long CREATED_TIMESTAMP = 10L;
    private static final long LATER_TIMESTAMP = 20L;
    private static final long PRE_0_49_TIMESTAMP = 5L;

    private final FixFeeCollectorTokenAccountStatusMigration migration;
    private final RecordFileRepository recordFileRepository;
    private final TokenAccountRepository tokenAccountRepository;
    private final TokenRepository tokenRepository;

    @BeforeEach
    void setup() {
        recordFile(0, 48, 0L, PRE_0_49_TIMESTAMP);
        recordFile(0, 49, PRE_0_49_TIMESTAMP + 1, LATER_TIMESTAMP * 10);
    }

    @Test
    void empty() {
        migration.doMigrate();
        assertThat(tokenAccountRepository.findAll()).isEmpty();
        assertThat(findHistory(TokenAccountHistory.class)).isEmpty();
    }

    @Test
    void migrate() {
        // given
        // a fixed fee charged in the new token, the treasury and the collector are automatically associated
        final var token = token(CREATED_TIMESTAMP, TokenFreezeStatusEnum.FROZEN, TokenKycStatusEnum.REVOKED);
        final var collector = domainBuilder.entityId();
        fixedFee(token, collector, token.getTokenId());
        final var treasury = treasury(token);
        final var collectorAccount = enabled(token, collector);

        // a fractional fee
        final var fractionalToken = token(CREATED_TIMESTAMP, TokenFreezeStatusEnum.FROZEN, TokenKycStatusEnum.REVOKED);
        final var fractionalCollector = domainBuilder.entityId();
        customFee(fractionalToken, List.of(), List.of(fractionalFee(fractionalCollector)));
        final var fractionalTreasury = treasury(fractionalToken);
        final var fractionalCollectorAccount = enabled(fractionalToken, fractionalCollector);

        // not frozen by default, so only KYC is revoked
        final var kycOnlyToken = token(CREATED_TIMESTAMP, TokenFreezeStatusEnum.UNFROZEN, TokenKycStatusEnum.REVOKED);
        final var kycOnlyCollector = domainBuilder.entityId();
        fixedFee(kycOnlyToken, kycOnlyCollector, kycOnlyToken.getTokenId());
        final var kycOnlyCollectorAccount = enabled(kycOnlyToken, kycOnlyCollector);

        // no freeze key and no KYC key
        final var noKeyToken =
                token(CREATED_TIMESTAMP, TokenFreezeStatusEnum.NOT_APPLICABLE, TokenKycStatusEnum.NOT_APPLICABLE);
        final var noKeyCollector = domainBuilder.entityId();
        fixedFee(noKeyToken, noKeyCollector, noKeyToken.getTokenId());
        final var noKeyCollectorAccount = tokenAccount(
                noKeyToken,
                noKeyCollector,
                TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE,
                Range.atLeast(CREATED_TIMESTAMP));

        // the collector is the treasury
        final var treasuryCollectorToken =
                token(CREATED_TIMESTAMP, TokenFreezeStatusEnum.FROZEN, TokenKycStatusEnum.REVOKED);
        fixedFee(
                treasuryCollectorToken,
                treasuryCollectorToken.getTreasuryAccountId(),
                treasuryCollectorToken.getTokenId());
        final var treasuryCollectorAccount = treasury(treasuryCollectorToken);

        // a fixed fee charged in another token doesn't automatically associate the collector
        final var otherDenominationToken =
                token(CREATED_TIMESTAMP, TokenFreezeStatusEnum.FROZEN, TokenKycStatusEnum.REVOKED);
        final var otherDenominationCollector = domainBuilder.entityId();
        fixedFee(otherDenominationToken, otherDenominationCollector, domainBuilder.id());
        final var otherDenominationAccount = enabled(otherDenominationToken, otherDenominationCollector);

        // created prior to services 0.49.0
        final var preToken = token(PRE_0_49_TIMESTAMP, TokenFreezeStatusEnum.FROZEN, TokenKycStatusEnum.REVOKED);
        final var preCollector = domainBuilder.entityId();
        fixedFee(preToken, preCollector, preToken.getTokenId());
        final var preCollectorAccount = enabled(preToken, preCollector);

        // already correct
        final var fixedToken = token(CREATED_TIMESTAMP, TokenFreezeStatusEnum.FROZEN, TokenKycStatusEnum.REVOKED);
        final var fixedCollector = domainBuilder.entityId();
        fixedFee(fixedToken, fixedCollector, fixedToken.getTokenId());
        final var fixedCollectorAccount = tokenAccount(
                fixedToken,
                fixedCollector,
                TokenFreezeStatusEnum.FROZEN,
                TokenKycStatusEnum.REVOKED,
                Range.atLeast(CREATED_TIMESTAMP));

        // when
        migration.doMigrate();

        // then
        collectorAccount.setFreezeStatus(TokenFreezeStatusEnum.FROZEN);
        collectorAccount.setKycStatus(TokenKycStatusEnum.REVOKED);
        fractionalCollectorAccount.setFreezeStatus(TokenFreezeStatusEnum.FROZEN);
        fractionalCollectorAccount.setKycStatus(TokenKycStatusEnum.REVOKED);
        kycOnlyCollectorAccount.setKycStatus(TokenKycStatusEnum.REVOKED);
        assertThat(tokenAccountRepository.findAll())
                .containsExactlyInAnyOrder(
                        treasury,
                        collectorAccount,
                        fractionalTreasury,
                        fractionalCollectorAccount,
                        kycOnlyCollectorAccount,
                        noKeyCollectorAccount,
                        treasuryCollectorAccount,
                        otherDenominationAccount,
                        preCollectorAccount,
                        fixedCollectorAccount);
        assertThat(findHistory(TokenAccountHistory.class)).isEmpty();

        // when run again
        migration.doMigrate();

        // then nothing changes
        assertThat(tokenAccountRepository.findAll()).contains(collectorAccount, fractionalCollectorAccount);
        assertThat(findHistory(TokenAccountHistory.class)).isEmpty();
    }

    @Test
    void migrateEmptyKeyListKycKey() {
        // given
        final long post0_77Timestamp = LATER_TIMESTAMP * 20;
        recordFile(0, 77, LATER_TIMESTAMP * 10 + 1, post0_77Timestamp * 2);
        final var emptyKeyList = Key.newBuilder()
                .setKeyList(KeyList.getDefaultInstance())
                .build()
                .toByteArray();

        // since services 0.77.0, the empty key list KYC key disables KYC, so only the freeze status is fixed
        final var disabledToken = domainBuilder
                .token()
                .customize(t -> t.createdTimestamp(post0_77Timestamp)
                        .freezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .kycKey(emptyKeyList)
                        .kycStatus(TokenKycStatusEnum.REVOKED)
                        .timestampRange(Range.atLeast(post0_77Timestamp)))
                .persist();
        final var disabledCollector = domainBuilder.entityId();
        fixedFee(disabledToken, disabledCollector, disabledToken.getTokenId());
        final var disabledCollectorAccount = enabled(disabledToken, disabledCollector);

        // prior to services 0.77.0, the empty key list KYC key is a KYC key
        final var keyToken = domainBuilder
                .token()
                .customize(t -> t.createdTimestamp(CREATED_TIMESTAMP)
                        .freezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .kycKey(emptyKeyList)
                        .kycStatus(TokenKycStatusEnum.REVOKED)
                        .timestampRange(Range.atLeast(CREATED_TIMESTAMP)))
                .persist();
        final var keyCollector = domainBuilder.entityId();
        fixedFee(keyToken, keyCollector, keyToken.getTokenId());
        final var keyCollectorAccount = enabled(keyToken, keyCollector);

        // when
        migration.doMigrate();

        // then
        disabledCollectorAccount.setFreezeStatus(TokenFreezeStatusEnum.FROZEN);
        keyCollectorAccount.setFreezeStatus(TokenFreezeStatusEnum.FROZEN);
        keyCollectorAccount.setKycStatus(TokenKycStatusEnum.REVOKED);
        assertThat(tokenAccountRepository.findAll())
                .contains(disabledCollectorAccount, keyCollectorAccount)
                .hasSize(2);
        assertThat(findHistory(TokenAccountHistory.class)).isEmpty();
    }

    @Test
    void migrateKycDisabledTokens() {
        // given
        final long post0_77Timestamp = LATER_TIMESTAMP * 20;
        final long updateTimestamp = post0_77Timestamp + 10;
        recordFile(0, 77, LATER_TIMESTAMP * 10 + 1, post0_77Timestamp * 2);
        final var emptyKeyList = Key.newBuilder()
                .setKeyList(KeyList.getDefaultInstance())
                .build()
                .toByteArray();

        // created with KYC disabled since services 0.77.0, never updated
        final var disabled = kycToken(post0_77Timestamp, emptyKeyList, Range.atLeast(post0_77Timestamp));

        // created with KYC disabled since services 0.77.0, then updated, so the creation snapshot is in history
        final var updatedHistory = domainBuilder
                .tokenHistory()
                .customize(t -> t.createdTimestamp(post0_77Timestamp)
                        .kycKey(emptyKeyList)
                        .kycStatus(TokenKycStatusEnum.REVOKED)
                        .timestampRange(Range.closedOpen(post0_77Timestamp, updateTimestamp)))
                .persist();
        final var updated = domainBuilder
                .token()
                .customize(t -> t.createdTimestamp(post0_77Timestamp)
                        .kycKey(emptyKeyList)
                        .kycStatus(TokenKycStatusEnum.REVOKED)
                        .timestampRange(Range.atLeast(updateTimestamp))
                        .tokenId(updatedHistory.getTokenId()))
                .persist();

        // created prior to services 0.77.0, the empty key list KYC key is a KYC key
        final var preKeyList = kycToken(CREATED_TIMESTAMP, emptyKeyList, Range.atLeast(CREATED_TIMESTAMP));

        // created with a KYC key
        final var withKey = kycToken(post0_77Timestamp, domainBuilder.key(), Range.atLeast(post0_77Timestamp));

        // created with a KYC key, which is removed by a token update, so the accounts associated before the removal
        // keep their KYC status
        final var removedHistory = domainBuilder
                .tokenHistory()
                .customize(t -> t.createdTimestamp(post0_77Timestamp)
                        .kycStatus(TokenKycStatusEnum.REVOKED)
                        .timestampRange(Range.closedOpen(post0_77Timestamp, updateTimestamp)))
                .persist();
        final var removed = domainBuilder
                .token()
                .customize(t -> t.createdTimestamp(post0_77Timestamp)
                        .kycKey(emptyKeyList)
                        .kycStatus(TokenKycStatusEnum.REVOKED)
                        .timestampRange(Range.atLeast(updateTimestamp))
                        .tokenId(removedHistory.getTokenId()))
                .persist();

        // when
        migration.doMigrate();

        // then
        disabled.setKycStatus(TokenKycStatusEnum.GRANTED);
        updated.setKycStatus(TokenKycStatusEnum.GRANTED);
        updatedHistory.setKycStatus(TokenKycStatusEnum.GRANTED);
        assertThat(tokenRepository.findAll())
                .containsExactlyInAnyOrder(disabled, updated, preKeyList, withKey, removed);
        assertThat(findHistory(TokenHistory.class)).containsExactlyInAnyOrder(updatedHistory, removedHistory);

        // when run again
        migration.doMigrate();

        // then nothing changes
        assertThat(tokenRepository.findAll())
                .containsExactlyInAnyOrder(disabled, updated, preKeyList, withKey, removed);
        assertThat(findHistory(TokenHistory.class)).containsExactlyInAnyOrder(updatedHistory, removedHistory);
    }

    @Test
    void migratePrunedRecordFile() {
        // given
        // created prior to services 0.49.0, and its record file is pruned by retention while the token, the fee
        // schedule and the token account snapshots at the token creation are kept
        final var token = token(PRE_0_49_TIMESTAMP, TokenFreezeStatusEnum.FROZEN, TokenKycStatusEnum.REVOKED);
        final var collector = domainBuilder.entityId();
        fixedFee(token, collector, token.getTokenId());
        final var collectorAccount = enabled(token, collector);
        recordFileRepository.prune(PRE_0_49_TIMESTAMP);

        // when
        migration.doMigrate();

        // then
        assertThat(recordFileRepository.count()).isOne();
        assertThat(tokenAccountRepository.findAll()).containsExactly(collectorAccount);
        assertThat(findHistory(TokenAccountHistory.class)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(textBlock = """
            freeze,           TOKENFREEZE,     true,  10, FROZEN,   GRANTED, FROZEN,   REVOKED
            unfreeze,         TOKENUNFREEZE,   true,  10, UNFROZEN, GRANTED, UNFROZEN, REVOKED
            revoke kyc,       TOKENREVOKEKYC,  true,  10, UNFROZEN, REVOKED, FROZEN,   REVOKED
            grant kyc,        TOKENGRANTKYC,   true,  10, UNFROZEN, GRANTED, FROZEN,   GRANTED
            unknown,          ,                true,  10, UNFROZEN, GRANTED, UNFROZEN, GRANTED
            unknown freeze,   ,                true,  10, FROZEN,   GRANTED, FROZEN,   REVOKED
            dissociated,      TOKENDISSOCIATE, false, 10, UNFROZEN, GRANTED, UNFROZEN, GRANTED
            associated again, TOKENASSOCIATE,  true,  20, UNFROZEN, GRANTED, UNFROZEN, GRANTED
            """)
    void migrateSubsequentChange(
            String name,
            @Nullable TransactionType type,
            boolean associated,
            long createdTimestamp,
            TokenFreezeStatusEnum freezeStatus,
            TokenKycStatusEnum kycStatus,
            TokenFreezeStatusEnum expectedFreezeStatus,
            TokenKycStatusEnum expectedKycStatus) {
        // given
        // associated at the token creation and a subsequent change at LATER_TIMESTAMP
        final var token = token(CREATED_TIMESTAMP, TokenFreezeStatusEnum.FROZEN, TokenKycStatusEnum.REVOKED);
        final var collector = domainBuilder.entityId();
        fixedFee(token, collector, token.getTokenId());
        tokenAccountHistory(
                token,
                collector,
                TokenFreezeStatusEnum.UNFROZEN,
                TokenKycStatusEnum.GRANTED,
                Range.closedOpen(CREATED_TIMESTAMP, LATER_TIMESTAMP));
        final var current = domainBuilder
                .tokenAccount()
                .customize(t -> t.accountId(collector.getId())
                        .associated(associated)
                        .createdTimestamp(createdTimestamp)
                        .freezeStatus(freezeStatus)
                        .kycStatus(kycStatus)
                        .timestampRange(Range.atLeast(LATER_TIMESTAMP))
                        .tokenId(token.getTokenId()))
                .persist();
        if (type != null) {
            transaction(LATER_TIMESTAMP, type);
        }

        // when
        migration.doMigrate();

        // then
        current.setFreezeStatus(expectedFreezeStatus);
        current.setKycStatus(expectedKycStatus);
        assertThat(tokenAccountRepository.findAll()).contains(current);
        assertThat(findHistory(TokenAccountHistory.class))
                .extracting(
                        TokenAccountHistory::getTimestampRange,
                        TokenAccountHistory::getFreezeStatus,
                        TokenAccountHistory::getKycStatus)
                .containsExactly(tuple(
                        Range.closedOpen(CREATED_TIMESTAMP, LATER_TIMESTAMP),
                        TokenFreezeStatusEnum.FROZEN,
                        TokenKycStatusEnum.REVOKED));
    }

    @Test
    void migrateTreasuryChange() {
        // given
        // the collector becomes the treasury at LATER_TIMESTAMP, without a token account snapshot at the change
        final var collector = domainBuilder.entityId();
        final var tokenId = domainBuilder.id();
        domainBuilder
                .tokenHistory()
                .customize(t -> t.createdTimestamp(CREATED_TIMESTAMP)
                        .freezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .kycStatus(TokenKycStatusEnum.REVOKED)
                        .timestampRange(Range.closedOpen(CREATED_TIMESTAMP, LATER_TIMESTAMP))
                        .tokenId(tokenId))
                .persist();
        domainBuilder
                .token()
                .customize(t -> t.createdTimestamp(CREATED_TIMESTAMP)
                        .freezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .kycStatus(TokenKycStatusEnum.REVOKED)
                        .timestampRange(Range.atLeast(LATER_TIMESTAMP))
                        .tokenId(tokenId)
                        .treasuryAccountId(collector))
                .persist();
        domainBuilder
                .customFee()
                .customize(c -> c.entityId(tokenId)
                        .fixedFees(List.of(fixedFee(collector, tokenId)))
                        .fractionalFees(List.of())
                        .royaltyFees(List.of())
                        .timestampRange(Range.atLeast(CREATED_TIMESTAMP)))
                .persist();
        final var current = domainBuilder
                .tokenAccount()
                .customize(t -> t.accountId(collector.getId())
                        .createdTimestamp(CREATED_TIMESTAMP)
                        .freezeStatus(TokenFreezeStatusEnum.UNFROZEN)
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .timestampRange(Range.atLeast(CREATED_TIMESTAMP))
                        .tokenId(tokenId))
                .persist();

        // when
        migration.doMigrate();

        // then
        // the snapshot is split at the treasury change, and only the part prior to it is fixed
        current.setTimestampRange(Range.atLeast(LATER_TIMESTAMP));
        assertThat(tokenAccountRepository.findAll()).containsExactly(current);
        assertThat(findHistory(TokenAccountHistory.class))
                .extracting(
                        TokenAccountHistory::getTimestampRange,
                        TokenAccountHistory::getFreezeStatus,
                        TokenAccountHistory::getKycStatus)
                .containsExactly(tuple(
                        Range.closedOpen(CREATED_TIMESTAMP, LATER_TIMESTAMP),
                        TokenFreezeStatusEnum.FROZEN,
                        TokenKycStatusEnum.REVOKED));

        // when run again
        migration.doMigrate();

        // then nothing changes
        assertThat(tokenAccountRepository.findAll()).containsExactly(current);
        assertThat(findHistory(TokenAccountHistory.class)).hasSize(1);
    }

    @Test
    void migrateTreasuryChangeInHistory() {
        // given
        // the collector becomes the treasury at LATER_TIMESTAMP, inside a history snapshot that ends afterward
        final long unfreezeTimestamp = LATER_TIMESTAMP + 10;
        final var collector = domainBuilder.entityId();
        final var tokenId = domainBuilder.id();
        domainBuilder
                .tokenHistory()
                .customize(t -> t.createdTimestamp(CREATED_TIMESTAMP)
                        .freezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .kycStatus(TokenKycStatusEnum.REVOKED)
                        .timestampRange(Range.closedOpen(CREATED_TIMESTAMP, LATER_TIMESTAMP))
                        .tokenId(tokenId))
                .persist();
        final var token = domainBuilder
                .token()
                .customize(t -> t.createdTimestamp(CREATED_TIMESTAMP)
                        .freezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .kycStatus(TokenKycStatusEnum.REVOKED)
                        .timestampRange(Range.atLeast(LATER_TIMESTAMP))
                        .tokenId(tokenId)
                        .treasuryAccountId(collector))
                .persist();
        fixedFee(token, collector, tokenId);
        tokenAccountHistory(
                token,
                collector,
                TokenFreezeStatusEnum.UNFROZEN,
                TokenKycStatusEnum.GRANTED,
                Range.closedOpen(CREATED_TIMESTAMP, unfreezeTimestamp));
        final var current = tokenAccount(
                token,
                collector,
                TokenFreezeStatusEnum.UNFROZEN,
                TokenKycStatusEnum.GRANTED,
                Range.atLeast(unfreezeTimestamp));
        transaction(unfreezeTimestamp, TransactionType.TOKENUNFREEZE);

        // when
        migration.doMigrate();

        // then
        assertThat(tokenAccountRepository.findAll()).containsExactly(current);
        assertThat(findHistory(TokenAccountHistory.class))
                .extracting(
                        TokenAccountHistory::getTimestampRange,
                        TokenAccountHistory::getFreezeStatus,
                        TokenAccountHistory::getKycStatus)
                .containsExactlyInAnyOrder(
                        tuple(
                                Range.closedOpen(CREATED_TIMESTAMP, LATER_TIMESTAMP),
                                TokenFreezeStatusEnum.FROZEN,
                                TokenKycStatusEnum.REVOKED),
                        tuple(
                                Range.closedOpen(LATER_TIMESTAMP, unfreezeTimestamp),
                                TokenFreezeStatusEnum.UNFROZEN,
                                TokenKycStatusEnum.GRANTED));
    }

    @Test
    void migrateTreasuryChangeWithSnapshot() {
        // given
        // the collector becomes the treasury at LATER_TIMESTAMP, with a token account snapshot at the change
        final var collector = domainBuilder.entityId();
        final var tokenId = domainBuilder.id();
        domainBuilder
                .tokenHistory()
                .customize(t -> t.createdTimestamp(CREATED_TIMESTAMP)
                        .freezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .kycStatus(TokenKycStatusEnum.REVOKED)
                        .timestampRange(Range.closedOpen(CREATED_TIMESTAMP, LATER_TIMESTAMP))
                        .tokenId(tokenId))
                .persist();
        final var token = domainBuilder
                .token()
                .customize(t -> t.createdTimestamp(CREATED_TIMESTAMP)
                        .freezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .kycStatus(TokenKycStatusEnum.REVOKED)
                        .timestampRange(Range.atLeast(LATER_TIMESTAMP))
                        .tokenId(tokenId)
                        .treasuryAccountId(collector))
                .persist();
        fixedFee(token, collector, tokenId);
        tokenAccountHistory(
                token,
                collector,
                TokenFreezeStatusEnum.UNFROZEN,
                TokenKycStatusEnum.GRANTED,
                Range.closedOpen(CREATED_TIMESTAMP, LATER_TIMESTAMP));
        final var current = tokenAccount(
                token,
                collector,
                TokenFreezeStatusEnum.UNFROZEN,
                TokenKycStatusEnum.GRANTED,
                Range.atLeast(LATER_TIMESTAMP));
        transaction(LATER_TIMESTAMP, TransactionType.TOKENUPDATE);

        // when
        migration.doMigrate();

        // then
        assertThat(tokenAccountRepository.findAll()).containsExactly(current);
        assertThat(findHistory(TokenAccountHistory.class))
                .extracting(
                        TokenAccountHistory::getTimestampRange,
                        TokenAccountHistory::getFreezeStatus,
                        TokenAccountHistory::getKycStatus)
                .containsExactly(tuple(
                        Range.closedOpen(CREATED_TIMESTAMP, LATER_TIMESTAMP),
                        TokenFreezeStatusEnum.FROZEN,
                        TokenKycStatusEnum.REVOKED));
    }

    private void customFee(Token token, List<FixedFee> fixedFees, List<FractionalFee> fractionalFees) {
        domainBuilder
                .customFee()
                .customize(c -> c.entityId(token.getTokenId())
                        .fixedFees(fixedFees)
                        .fractionalFees(fractionalFees)
                        .royaltyFees(List.of())
                        .timestampRange(Range.atLeast(token.getCreatedTimestamp())))
                .persist();
    }

    private TokenAccount enabled(Token token, EntityId accountId) {
        return tokenAccount(
                token,
                accountId,
                TokenFreezeStatusEnum.UNFROZEN,
                TokenKycStatusEnum.GRANTED,
                Range.atLeast(token.getCreatedTimestamp()));
    }

    private FixedFee fixedFee(EntityId collector, long denominatingTokenId) {
        return domainBuilder.fixedFee().toBuilder()
                .collectorAccountId(collector)
                .denominatingTokenId(EntityId.of(denominatingTokenId))
                .build();
    }

    private void fixedFee(Token token, EntityId collector, long denominatingTokenId) {
        customFee(token, List.of(fixedFee(collector, denominatingTokenId)), List.of());
    }

    private FractionalFee fractionalFee(EntityId collector) {
        return FractionalFee.builder()
                .collectorAccountId(collector)
                .denominator(10L)
                .numerator(1L)
                .build();
    }

    private Token kycToken(long createdTimestamp, byte[] kycKey, Range<Long> timestampRange) {
        return domainBuilder
                .token()
                .customize(t -> t.createdTimestamp(createdTimestamp)
                        .kycKey(kycKey)
                        .kycStatus(TokenKycStatusEnum.REVOKED)
                        .timestampRange(timestampRange))
                .persist();
    }

    private void recordFile(int major, int minor, long consensusStart, long consensusEnd) {
        domainBuilder
                .recordFile()
                .customize(r -> r.consensusStart(consensusStart)
                        .consensusEnd(consensusEnd)
                        .hapiVersionMajor(major)
                        .hapiVersionMinor(minor))
                .persist();
    }

    private Token token(long createdTimestamp, TokenFreezeStatusEnum freezeStatus, TokenKycStatusEnum kycStatus) {
        return domainBuilder
                .token()
                .customize(t -> t.createdTimestamp(createdTimestamp)
                        .freezeStatus(freezeStatus)
                        .kycStatus(kycStatus)
                        .timestampRange(Range.atLeast(createdTimestamp)))
                .persist();
    }

    private TokenAccount tokenAccount(
            Token token,
            EntityId accountId,
            TokenFreezeStatusEnum freezeStatus,
            TokenKycStatusEnum kycStatus,
            Range<Long> timestampRange) {
        return domainBuilder
                .tokenAccount()
                .customize(t -> t.accountId(accountId.getId())
                        .createdTimestamp(token.getCreatedTimestamp())
                        .freezeStatus(freezeStatus)
                        .kycStatus(kycStatus)
                        .timestampRange(timestampRange)
                        .tokenId(token.getTokenId()))
                .persist();
    }

    private void tokenAccountHistory(
            Token token,
            EntityId accountId,
            TokenFreezeStatusEnum freezeStatus,
            TokenKycStatusEnum kycStatus,
            Range<Long> timestampRange) {
        domainBuilder
                .tokenAccountHistory()
                .customize(t -> t.accountId(accountId.getId())
                        .createdTimestamp(token.getCreatedTimestamp())
                        .freezeStatus(freezeStatus)
                        .kycStatus(kycStatus)
                        .timestampRange(timestampRange)
                        .tokenId(token.getTokenId()))
                .persist();
    }

    private void transaction(long consensusTimestamp, TransactionType type) {
        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp).type(type.getProtoId()))
                .persist();
    }

    private TokenAccount treasury(Token token) {
        return enabled(token, token.getTreasuryAccountId());
    }
}
