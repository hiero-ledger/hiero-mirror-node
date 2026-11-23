// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.migration.FixDelegatedNftApproveForAllMigration.INTERVAL;
import static org.hiero.mirror.importer.migration.FixDelegatedNftApproveForAllMigration.MAINNET_START_TIMESTAMP;
import static org.hiero.mirror.importer.migration.FixDelegatedNftApproveForAllMigration.TESTNET_START_TIMESTAMP;

import com.google.common.collect.Range;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.AbstractNftAllowance;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.NftAllowance;
import org.hiero.mirror.common.domain.entity.NftAllowanceHistory;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.ImporterProperties.HederaNetwork;
import org.hiero.mirror.importer.repository.NftAllowanceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@RequiredArgsConstructor
@Tag("migration")
final class FixDelegatedNftApproveForAllMigrationTest extends ImporterIntegrationTest {

    private final ImporterProperties importerProperties;
    private final FixDelegatedNftApproveForAllMigration migration;
    private final NftAllowanceRepository nftAllowanceRepository;

    @AfterEach
    void cleanup() {
        importerProperties.setNetwork(HederaNetwork.TESTNET);
    }

    @Test
    void beforeStartTimestamp() {
        // given
        final long timestamp = TESTNET_START_TIMESTAMP - 1;
        final var previous = persistPreviousAllowance(timestamp);
        final var revoked = persistRevocation(previous, timestamp);
        persistCryptoApproveAllowance(timestamp);
        persistDelegatedNft(revoked);
        persistRecordFile(TESTNET_START_TIMESTAMP + 100);

        // when
        migration.doMigrate();

        // then
        assertUntouched(previous, revoked);
    }

    @Test
    void checksum() {
        assertThat(migration.getChecksum()).isOne();
    }

    @Test
    void consecutiveHistoryRevocations() {
        // given the same allowance wrongly revoked twice in the same week, followed by a regular revision
        final long timestamp1 = TESTNET_START_TIMESTAMP + 10;
        final long timestamp2 = timestamp1 + 100;
        final long timestamp3 = timestamp2 + 100;
        final var previous = persistPreviousAllowance(timestamp1);
        final var revoked1 = persistRevocationHistory(previous, Range.closedOpen(timestamp1, timestamp2));
        final var revoked2 = persistRevocationHistory(previous, Range.closedOpen(timestamp2, timestamp3));
        final var current = domainBuilder
                .nftAllowance()
                .customize(a -> a.approvedForAll(true)
                        .owner(previous.getOwner())
                        .spender(previous.getSpender())
                        .timestampRange(Range.atLeast(timestamp3))
                        .tokenId(previous.getTokenId()))
                .persist();
        persistCryptoApproveAllowance(timestamp1);
        persistCryptoApproveAllowance(timestamp2);
        persistDelegatedNft(revoked1);
        persistDelegatedNft(revoked2);
        persistRecordFile(timestamp3);

        // when
        migration.doMigrate();

        // then
        assertThat(nftAllowanceRepository.findAll()).containsExactly(current);
        assertThat(findHistory(NftAllowance.class))
                .containsExactly(toNftAllowance(previous, Range.closedOpen(previous.getTimestampLower(), timestamp3)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void consecutiveRevocations(final boolean sameWeek) {
        // given the same allowance wrongly revoked twice
        final long timestamp1 = TESTNET_START_TIMESTAMP + 10;
        final long timestamp2 = timestamp1 + (sameWeek ? 100 : 2 * INTERVAL);
        final var previous = persistPreviousAllowance(timestamp1);
        final var revoked1 = persistRevocationHistory(previous, Range.closedOpen(timestamp1, timestamp2));
        final var revoked2 = persistRevocation(previous, timestamp2);
        persistCryptoApproveAllowance(timestamp1);
        persistCryptoApproveAllowance(timestamp2);
        persistDelegatedNft(revoked1);
        persistDelegatedNft(revoked2);
        persistRecordFile(timestamp2);

        // when
        migration.doMigrate();

        // then
        assertRestored(previous);
    }

    @ParameterizedTest
    @ValueSource(strings = {HederaNetwork.MAINNET, HederaNetwork.TESTNET})
    void currentRevocation(final String network) {
        // given a revocation in the first HAPI 0.77.0 transaction
        importerProperties.setNetwork(network);
        final long timestamp =
                network.equals(HederaNetwork.MAINNET) ? MAINNET_START_TIMESTAMP : TESTNET_START_TIMESTAMP;
        final var previous = persistPreviousAllowance(timestamp);
        final var revoked = persistRevocation(previous, timestamp);
        persistCryptoApproveAllowance(timestamp);
        persistDelegatedNft(revoked);
        persistRecordFile(timestamp);

        // when
        migration.doMigrate();

        // then
        assertRestored(previous);
    }

    @Test
    void currentRevocationAfterOwnerRevocation() {
        // given the previous revision is a regular revocation by the owner
        final long timestamp = TESTNET_START_TIMESTAMP + 10;
        final var previous = domainBuilder
                .nftAllowanceHistory()
                .customize(a -> a.approvedForAll(false).timestampRange(Range.closedOpen(timestamp - 100, timestamp)))
                .persist();
        final var revoked = persistRevocation(previous, timestamp);
        persistCryptoApproveAllowance(timestamp);
        persistDelegatedNft(revoked);
        persistRecordFile(timestamp);

        // when
        migration.doMigrate();

        // then the owner revocation is restored as the current revision
        assertRestored(previous);
    }

    @Test
    void currentRevocationWithoutPrevious() {
        // given
        final long timestamp = TESTNET_START_TIMESTAMP + 10;
        final var revoked = domainBuilder
                .nftAllowance()
                .customize(a -> a.approvedForAll(false).timestampRange(Range.atLeast(timestamp)))
                .persist();
        persistCryptoApproveAllowance(timestamp);
        persistDelegatedNft(revoked);
        persistRecordFile(timestamp);

        // when
        migration.doMigrate();

        // then
        assertThat(nftAllowanceRepository.findAll()).isEmpty();
        assertThat(findHistory(NftAllowance.class)).isEmpty();
    }

    @Test
    void empty() {
        // when
        migration.doMigrate();

        // then
        assertThat(nftAllowanceRepository.findAll()).isEmpty();
        assertThat(findHistory(NftAllowance.class)).isEmpty();
    }

    @Test
    void historyRevocation() {
        // given
        final long timestamp = TESTNET_START_TIMESTAMP + 10;
        final var previous = persistPreviousAllowance(timestamp);
        final var revoked = persistRevocationHistory(previous, Range.closedOpen(timestamp, timestamp + 100));
        final var current = domainBuilder
                .nftAllowance()
                .customize(a -> a.approvedForAll(true)
                        .owner(previous.getOwner())
                        .spender(previous.getSpender())
                        .timestampRange(Range.atLeast(revoked.getTimestampUpper()))
                        .tokenId(previous.getTokenId()))
                .persist();
        persistCryptoApproveAllowance(timestamp);
        persistDelegatedNft(revoked);
        persistRecordFile(revoked.getTimestampUpper());

        // when
        migration.doMigrate();

        // then
        assertThat(nftAllowanceRepository.findAll()).containsExactly(current);
        assertThat(findHistory(NftAllowance.class))
                .containsExactly(toNftAllowance(
                        previous, Range.closedOpen(previous.getTimestampLower(), revoked.getTimestampUpper())));
    }

    @Test
    void historyRevocationWithoutPrevious() {
        // given
        final long timestamp = TESTNET_START_TIMESTAMP + 10;
        final var revoked = domainBuilder
                .nftAllowanceHistory()
                .customize(a -> a.approvedForAll(false).timestampRange(Range.closedOpen(timestamp, timestamp + 100)))
                .persist();
        final var current = domainBuilder
                .nftAllowance()
                .customize(a -> a.approvedForAll(true)
                        .owner(revoked.getOwner())
                        .spender(revoked.getSpender())
                        .timestampRange(Range.atLeast(revoked.getTimestampUpper()))
                        .tokenId(revoked.getTokenId()))
                .persist();
        persistCryptoApproveAllowance(timestamp);
        persistDelegatedNft(revoked);
        persistRecordFile(revoked.getTimestampUpper());

        // when
        migration.doMigrate();

        // then the revocation is removed, leaving no revision before the current one
        assertThat(nftAllowanceRepository.findAll()).containsExactly(current);
        assertThat(findHistory(NftAllowance.class)).isEmpty();
    }

    @Test
    void multipleSerials() {
        // given a delegated nft allowance with two serials
        final long timestamp = TESTNET_START_TIMESTAMP + 10;
        final var previous = persistPreviousAllowance(timestamp);
        final var revoked = persistRevocation(previous, timestamp);
        persistCryptoApproveAllowance(timestamp);
        persistDelegatedNft(revoked);
        persistDelegatedNft(revoked);
        persistRecordFile(timestamp);

        // when
        migration.doMigrate();

        // then
        assertRestored(previous);
    }

    @Test
    void notCryptoApproveAllowance() {
        // given
        final long timestamp = TESTNET_START_TIMESTAMP + 10;
        final var previous = persistPreviousAllowance(timestamp);
        final var revoked = persistRevocation(previous, timestamp);
        domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp))
                .persist();
        persistDelegatedNft(revoked);
        persistRecordFile(timestamp);

        // when
        migration.doMigrate();

        // then
        assertUntouched(previous, revoked);
    }

    @Test
    void notDelegated() {
        // given an owner revocation with a non-delegated serial allowance in the same transaction
        final long timestamp = TESTNET_START_TIMESTAMP + 10;
        final var previous = persistPreviousAllowance(timestamp);
        final var revoked = persistRevocation(previous, timestamp);
        persistCryptoApproveAllowance(timestamp);
        domainBuilder
                .nft()
                .customize(n -> n.accountId(EntityId.of(previous.getOwner()))
                        .spender(previous.getSpender())
                        .timestampRange(Range.atLeast(timestamp))
                        .tokenId(previous.getTokenId()))
                .persist();
        persistRecordFile(timestamp);

        // when
        migration.doMigrate();

        // then
        assertUntouched(previous, revoked);
    }

    @Test
    void otherNetwork() {
        // given
        importerProperties.setNetwork(HederaNetwork.PREVIEWNET);
        final long timestamp = TESTNET_START_TIMESTAMP + 10;
        final var previous = persistPreviousAllowance(timestamp);
        final var revoked = persistRevocation(previous, timestamp);
        persistCryptoApproveAllowance(timestamp);
        persistDelegatedNft(revoked);
        persistRecordFile(timestamp);

        // when
        migration.doMigrate();

        // then
        assertUntouched(previous, revoked);
    }

    @Test
    void repeatableMigration() {
        // given
        final long timestamp = TESTNET_START_TIMESTAMP + 10;
        final var previous = persistPreviousAllowance(timestamp);
        final var revoked = persistRevocation(previous, timestamp);
        persistCryptoApproveAllowance(timestamp);
        persistDelegatedNft(revoked);
        persistRecordFile(timestamp);
        migration.doMigrate();

        // when
        migration.doMigrate();

        // then
        assertRestored(previous);
    }

    private void assertRestored(final NftAllowanceHistory previous) {
        assertThat(nftAllowanceRepository.findAll())
                .containsExactly(toNftAllowance(previous, Range.atLeast(previous.getTimestampLower())));
        assertThat(findHistory(NftAllowance.class)).isEmpty();
    }

    private void assertUntouched(final NftAllowanceHistory previous, final NftAllowance revoked) {
        assertThat(nftAllowanceRepository.findAll()).containsExactly(revoked);
        assertThat(findHistory(NftAllowance.class))
                .containsExactly(toNftAllowance(previous, previous.getTimestampRange()));
    }

    private void persistCryptoApproveAllowance(final long timestamp) {
        domainBuilder
                .transaction()
                .customize(
                        t -> t.consensusTimestamp(timestamp).type(TransactionType.CRYPTOAPPROVEALLOWANCE.getProtoId()))
                .persist();
    }

    // Persists the delegated nft serial allowance in the same transaction as the revocation
    private void persistDelegatedNft(final AbstractNftAllowance revoked) {
        if (revoked.getTimestampUpper() == null) {
            domainBuilder
                    .nft()
                    .customize(n -> n.accountId(EntityId.of(revoked.getOwner()))
                            .delegatingSpender(domainBuilder.id())
                            .spender(revoked.getSpender())
                            .timestampRange(revoked.getTimestampRange())
                            .tokenId(revoked.getTokenId()))
                    .persist();
            return;
        }

        domainBuilder
                .nftHistory()
                .customize(n -> n.accountId(EntityId.of(revoked.getOwner()))
                        .delegatingSpender(domainBuilder.id())
                        .spender(revoked.getSpender())
                        .timestampRange(revoked.getTimestampRange())
                        .tokenId(revoked.getTokenId()))
                .persist();
    }

    private NftAllowanceHistory persistPreviousAllowance(final long upperTimestamp) {
        return domainBuilder
                .nftAllowanceHistory()
                .customize(a ->
                        a.approvedForAll(true).timestampRange(Range.closedOpen(upperTimestamp - 100, upperTimestamp)))
                .persist();
    }

    private void persistRecordFile(final long consensusEnd) {
        domainBuilder
                .recordFile()
                .customize(r -> r.consensusEnd(consensusEnd).consensusStart(consensusEnd - 1))
                .persist();
    }

    private NftAllowance persistRevocation(final AbstractNftAllowance previous, final long timestamp) {
        return domainBuilder
                .nftAllowance()
                .customize(a -> a.approvedForAll(false)
                        .owner(previous.getOwner())
                        .spender(previous.getSpender())
                        .timestampRange(Range.atLeast(timestamp))
                        .tokenId(previous.getTokenId()))
                .persist();
    }

    private NftAllowanceHistory persistRevocationHistory(
            final AbstractNftAllowance previous, final Range<Long> timestampRange) {
        return domainBuilder
                .nftAllowanceHistory()
                .customize(a -> a.approvedForAll(false)
                        .owner(previous.getOwner())
                        .spender(previous.getSpender())
                        .timestampRange(timestampRange)
                        .tokenId(previous.getTokenId()))
                .persist();
    }

    private static NftAllowance toNftAllowance(final AbstractNftAllowance allowance, final Range<Long> timestampRange) {
        return NftAllowance.builder()
                .approvedForAll(allowance.isApprovedForAll())
                .owner(allowance.getOwner())
                .payerAccountId(allowance.getPayerAccountId())
                .spender(allowance.getSpender())
                .timestampRange(timestampRange)
                .tokenId(allowance.getTokenId())
                .build();
    }
}
