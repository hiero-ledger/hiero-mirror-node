// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.hiero.mirror.common.domain.DomainWrapper;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.NftAllowance;
import org.hiero.mirror.common.domain.entity.NftAllowance.NftAllowanceBuilder;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.repository.NftAllowanceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
@Tag("migration")
class SyntheticNftAllowanceOwnerMigrationTest extends ImporterIntegrationTest {

    private final SyntheticNftAllowanceOwnerMigration migration;
    private final NftAllowanceRepository nftAllowanceRepository;

    private NftAllowance nftAllowancePreMigration;
    private NftAllowance collidedNftAllowance;
    private Pair<NftAllowance, List<NftAllowance>> incorrectNftAllowancePair;
    private Pair<NftAllowance, List<NftAllowance>> unaffectedNftAllowancePair;
    private Pair<NftAllowance, List<NftAllowance>> correctNftAllowancePair;
    private NftAllowance newNftAllowance;

    private EntityId contractResultSenderId;
    private EntityId correctContractResultSenderId;
    private EntityId incorrectOwnerAccountId;
    private EntityId ownerAccountId;
    private EntityId ownerPreMigration;
    private EntityId spenderId;
    private EntityId tokenId;
    private long contractResultConsensusTimestamp;

    @BeforeEach
    void beforeEach() {
        contractResultSenderId = domainBuilder.entityId();
        correctContractResultSenderId = domainBuilder.entityId();
        incorrectOwnerAccountId = domainBuilder.entityId();
        ownerAccountId = domainBuilder.entityId();
        ownerPreMigration = domainBuilder.entityId();
        spenderId = domainBuilder.entityId();
        tokenId = domainBuilder.entityId();
        contractResultConsensusTimestamp = domainBuilder.timestamp();
    }

    @AfterEach
    void afterEach() throws Exception {
        Field activeField = migration.getClass().getDeclaredField("executed");
        activeField.setAccessible(true);
        activeField.set(migration, new AtomicBoolean(false));
    }

    @Test
    void checksum() {
        assertThat(migration.getChecksum()).isOne();
    }

    @Test
    void empty() {
        migration.doMigrate();
        assertThat(nftAllowanceRepository.findAll()).isEmpty();
        assertThat(findHistory(NftAllowance.class)).isEmpty();
    }

    @Test
    void migrate() {
        // given
        setup();

        // An nft allowance with a contract result that has a null sender id should not be migrated
        var nullSenderIdNftAllowancePair = generateNftAllowanceWithNullSenderId(incorrectOwnerAccountId);

        // when
        migration.doMigrate();

        // then
        assertContractResultSenderId(nullSenderIdNftAllowancePair);
    }

    @Test
    void repeatableMigration() {
        // given
        setup();

        migration.doMigrate();
        var firstPassNftAllowances = nftAllowanceRepository.findAll();
        var firstPassHistory = findHistory(NftAllowance.class);

        // when
        migration.doMigrate();
        var secondPassNftAllowances = nftAllowanceRepository.findAll();
        var secondPassHistory = findHistory(NftAllowance.class);

        // then
        assertThat(firstPassNftAllowances).containsExactlyInAnyOrderElementsOf(secondPassNftAllowances);
        assertThat(firstPassHistory).containsExactlyInAnyOrderElementsOf(secondPassHistory);
    }

    @Test
    void onEnd() {
        // given
        setup();

        // An nft allowance with a contract result that has a null sender id should not be migrated
        var nullSenderIdNftAllowancePair = generateNftAllowanceWithNullSenderId(incorrectOwnerAccountId);

        domainBuilder
                .recordFile()
                .customize(r -> r.hapiVersionMajor(0)
                        .hapiVersionMinor(36)
                        .hapiVersionPatch(10)
                        .consensusStart(1568415600193620000L)
                        .consensusEnd(1568415600193620001L))
                .persist();
        domainBuilder
                .recordFile()
                .customize(r -> r.hapiVersionMajor(0)
                        .hapiVersionMinor(36)
                        .hapiVersionPatch(10)
                        .consensusStart(1568415600183620000L)
                        .consensusEnd(1568415600183620001L))
                .persist();

        // when
        migration.onEnd(RecordFile.builder()
                .consensusStart(1568415600193620009L)
                .consensusEnd(1568415600193620010L)
                .hapiVersionMajor(0)
                .hapiVersionMinor(37)
                .hapiVersionPatch(1)
                .build());

        // then
        assertContractResultSenderId(nullSenderIdNftAllowancePair);
    }

    @Test
    void onEndHapiVersionNotMatched() {
        // given
        // Nft allowance and nft allowance history entries that have the incorrect owner
        incorrectNftAllowancePair = generateNftAllowance(contractResultSenderId, incorrectOwnerAccountId);

        // An nft allowance that has no contract result, but has primary key values that will end up matching those of
        // a migrated nft allowance.
        nftAllowancePreMigration = getCollidedNftAllowance(ownerAccountId, contractResultConsensusTimestamp - 2000L);

        // An nft allowance that has a contract result, once migrated it will have the same primary key fields as the
        // above nft allowance.
        collidedNftAllowance = getCollidedNftAllowance(ownerPreMigration, contractResultConsensusTimestamp);

        // The contract result for the collided nft allowance
        domainBuilder
                .contractResult()
                .customize(c -> c.senderId(ownerAccountId)
                        .payerAccountId(ownerPreMigration)
                        .consensusTimestamp(contractResultConsensusTimestamp))
                .persist();

        // The latest HAPI version in the DB is >=37.0, so migration will not be run.
        domainBuilder
                .recordFile()
                .customize(r -> r.hapiVersionMajor(0)
                        .hapiVersionMinor(37)
                        .hapiVersionPatch(0)
                        .consensusStart(1568415600193610000L)
                        .consensusEnd(1568415600193610002L))
                .persist();

        // when
        migration.onEnd(RecordFile.builder()
                .consensusStart(1568415600193620000L)
                .consensusEnd(1568415600193620001L)
                .hapiVersionMajor(0)
                .hapiVersionMinor(37)
                .hapiVersionPatch(1)
                .build());

        // then
        var expected = new ArrayList<>(List.of(incorrectNftAllowancePair.getLeft()));
        // The primary key will not be updated since migration is not run
        expected.add(collidedNftAllowance);
        expected.add(nftAllowancePreMigration);
        assertThat(nftAllowanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);

        // The history of the nft allowance should also be updated with the corrected owner. This will not happen since
        // the migration is not run
        var expectedHistory = new ArrayList<>(incorrectNftAllowancePair.getRight());
        assertThat(findHistory(NftAllowance.class)).containsExactlyInAnyOrderElementsOf(expectedHistory);
    }

    private NftAllowance getCollidedNftAllowance(EntityId ownerPreMigration, long contractResultConsensus) {
        return domainBuilder
                .nftAllowance()
                .customize(t -> t.owner(ownerPreMigration.getId())
                        .payerAccountId(ownerPreMigration)
                        .spender(spenderId.getId())
                        .timestampRange(Range.atLeast(contractResultConsensus))
                        .tokenId(tokenId.getId()))
                .persist();
    }

    private void assertContractResultSenderId(Pair<NftAllowance, List<NftAllowance>> nullSenderIdNftAllowancePair) {
        var expected = new ArrayList<>(List.of(incorrectNftAllowancePair.getLeft()));
        // The owner should be set to the contract result sender id
        expected.forEach(t -> t.setOwner(contractResultSenderId.getId()));
        expected.add(unaffectedNftAllowancePair.getLeft());
        expected.add(correctNftAllowancePair.getLeft());
        expected.add(newNftAllowance);
        collidedNftAllowance.setOwner(ownerAccountId.getId());
        expected.add(collidedNftAllowance);
        expected.add(nullSenderIdNftAllowancePair.getLeft());
        assertThat(nftAllowanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);

        // The history of the nft allowance should also be updated with the corrected owner
        var expectedHistory = new ArrayList<>(incorrectNftAllowancePair.getRight());
        // The owner should be set to the contract result sender id
        expectedHistory.forEach(t -> t.setOwner(contractResultSenderId.getId()));
        expectedHistory.addAll(unaffectedNftAllowancePair.getRight());
        expectedHistory.addAll(correctNftAllowancePair.getRight());
        expectedHistory.addAll(nullSenderIdNftAllowancePair.getRight());
        nftAllowancePreMigration.setTimestampUpper(contractResultConsensusTimestamp);
        expectedHistory.add(nftAllowancePreMigration);
        assertThat(findHistory(NftAllowance.class)).containsExactlyInAnyOrderElementsOf(expectedHistory);
    }

    private void setup() {
        // Nft allowance and nft allowance history entries that have the incorrect owner
        incorrectNftAllowancePair = generateNftAllowance(contractResultSenderId, incorrectOwnerAccountId);

        // An nft allowance can occur through HAPI and absent a corresponding Contract Result.
        // This nft allowance and it's history should be unaffected by the migration
        unaffectedNftAllowancePair = generateNftAllowance(null, null);

        // This problem has already been fixed. So there are nft allowances with the correct owner.
        // This nft allowance and it's history should be unaffected by the migration
        correctNftAllowancePair = generateNftAllowance(correctContractResultSenderId, correctContractResultSenderId);

        // A nft allowance that has no history and the correct owner, it should not be affected by the migration.
        newNftAllowance = domainBuilder.nftAllowance().persist();
        domainBuilder
                .contractResult()
                .customize(c -> c.senderId(EntityId.of(newNftAllowance.getOwner()))
                        .consensusTimestamp(newNftAllowance.getTimestampLower()))
                .persist();

        // An nft allowance that has no contract result, but has primary key values that will end up matching those of
        // a migrated nft allowance.
        nftAllowancePreMigration = getCollidedNftAllowance(ownerAccountId, contractResultConsensusTimestamp - 2000L);

        // An nft allowance that has a contract result, once migrated it will have the same primary key fields as the
        // above nft allowance.
        collidedNftAllowance = getCollidedNftAllowance(ownerPreMigration, contractResultConsensusTimestamp);

        // The contract result for the collided nft allowance
        domainBuilder
                .contractResult()
                .customize(c -> c.senderId(ownerAccountId)
                        .payerAccountId(ownerPreMigration)
                        .consensusTimestamp(contractResultConsensusTimestamp))
                .persist();
    }

    /**
     * Creates an nft allowance and two historical nft allowances to populate nft_allowance and
     * nft_allowance_history
     *
     * @param contractResultSenderId
     * @param ownerAccountId
     * @return the current nft allowance and the historical nft allowances
     */
    private Pair<NftAllowance, List<NftAllowance>> generateNftAllowance(
            EntityId contractResultSenderId, EntityId ownerAccountId) {
        var builder = domainBuilder.nftAllowance();
        if (contractResultSenderId != null) {
            builder.customize(nfta -> nfta.owner(ownerAccountId.getId()));
        }
        var currentNftAllowance = builder.persist();
        var nftHistory = generateNftAllowanceHistory(currentNftAllowance, builder);
        if (contractResultSenderId != null) {
            var contractResult = domainBuilder.contractResult();
            for (var nftAllowance : List.of(currentNftAllowance, nftHistory.get(0), nftHistory.get(1))) {
                contractResult
                        .customize(c ->
                                c.senderId(contractResultSenderId).consensusTimestamp(nftAllowance.getTimestampLower()))
                        .persist();
            }
        }

        return Pair.of(currentNftAllowance, nftHistory);
    }

    private Pair<NftAllowance, List<NftAllowance>> generateNftAllowanceWithNullSenderId(EntityId ownerAccountId) {
        var builder = domainBuilder.nftAllowance().customize(nfta -> nfta.owner(ownerAccountId.getId()));
        var currentNftAllowance = builder.persist();
        var nftHistory = generateNftAllowanceHistory(currentNftAllowance, builder);
        var contractResult = domainBuilder.contractResult();
        for (var nftAllowance : List.of(currentNftAllowance, nftHistory.get(0), nftHistory.get(1))) {
            contractResult
                    .customize(c -> c.senderId(null).consensusTimestamp(nftAllowance.getTimestampLower()))
                    .persist();
        }

        return Pair.of(currentNftAllowance, nftHistory);
    }

    private List<NftAllowance> generateNftAllowanceHistory(
            NftAllowance nftAllowance, DomainWrapper<NftAllowance, NftAllowanceBuilder<?, ?>> builder) {
        var range = nftAllowance.getTimestampRange();
        var rangeUpdate1 = Range.closedOpen(range.lowerEndpoint() - 2, range.lowerEndpoint() - 1);
        var rangeUpdate2 = Range.closedOpen(range.lowerEndpoint() - 1, range.lowerEndpoint());

        var update1 = builder.customize(nfta -> nfta.approvedForAll(false).timestampRange(rangeUpdate1))
                .get();
        var update2 = builder.customize(nfta -> nfta.approvedForAll(true).timestampRange(rangeUpdate2))
                .get();
        saveHistory(update1);
        saveHistory(update2);

        return List.of(update1, update2);
    }

    private void saveHistory(NftAllowance nftAllowance) {
        domainBuilder
                .nftAllowanceHistory()
                .customize(c -> c.approvedForAll(nftAllowance.isApprovedForAll())
                        .owner(nftAllowance.getOwner())
                        .payerAccountId(nftAllowance.getPayerAccountId())
                        .spender(nftAllowance.getSpender())
                        .timestampRange(nftAllowance.getTimestampRange())
                        .tokenId(nftAllowance.getTokenId()))
                .persist();
    }
}
