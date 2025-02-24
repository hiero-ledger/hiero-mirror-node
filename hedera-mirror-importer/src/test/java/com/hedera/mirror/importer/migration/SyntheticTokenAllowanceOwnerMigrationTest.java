// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import com.hedera.mirror.importer.repository.TokenAllowanceRepository;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
@Tag("migration")
class SyntheticTokenAllowanceOwnerMigrationTest extends ImporterIntegrationTest {

    private final SyntheticTokenAllowanceOwnerMigration migration;
    private final TokenAllowanceRepository tokenAllowanceRepository;
    private TokenAllowance tokenAllowancePreMigration;
    private TokenAllowance collidedTokenAllowance;
    private Pair<TokenAllowance, List<TokenAllowance>> incorrectTokenAllowancePair;
    private Pair<TokenAllowance, List<TokenAllowance>> unaffectedTokenAllowancePair;
    private Pair<TokenAllowance, List<TokenAllowance>> correctTokenAllowancePair;
    private TokenAllowance newTokenAllowance;
    private static final EntityId CONTRACT_RESULT_SENDER_ID = EntityId.of("0.0.2001");
    private static final EntityId CORRECT_CONTRACT_RESULT_SENDER_ID = EntityId.of("0.0.3001");
    private static final long INCORRECT_OWNER_ACCOUNT_ID = 1001L;
    private static final long OWNER_ACCOUNT_ID = 38L;
    private static final long OWNER_PRE_MIGRATION = 1322L;
    private static final long SPENDER = 39L;
    private static final long TOKEN_ID = 44L;
    private static final long CONTRACT_RESULT_CONSENSUS_TIMESTAMP = 1676546391434923004L;

    @AfterEach
    void after() throws Exception {
        Field activeField = migration.getClass().getDeclaredField("executed");
        activeField.setAccessible(true);
        activeField.set(migration, new AtomicBoolean(false));
    }

    @Test
    void checksum() {
        assertThat(migration.getChecksum()).isEqualTo(2);
    }

    @Test
    void empty() {
        migration.doMigrate();
        assertThat(tokenAllowanceRepository.findAll()).isEmpty();
        assertThat(findHistory(TokenAllowance.class)).isEmpty();
    }

    @Test
    void migrate() {
        // given
        setup();

        // when
        migration.doMigrate();

        // then
        assertContractResultSender();
    }

    @Test
    void repeatableMigration() {
        // given
        setup();

        migration.doMigrate();

        var firstPassTokenAllowances = tokenAllowanceRepository.findAll();
        var firstPassHistory = findHistory(TokenAllowance.class);

        // when
        migration.doMigrate();
        var secondPassTokenAllowances = tokenAllowanceRepository.findAll();
        var secondPassHistory = findHistory(TokenAllowance.class);

        // then
        assertThat(firstPassTokenAllowances).containsExactlyInAnyOrderElementsOf(secondPassTokenAllowances);
        assertThat(firstPassHistory).containsExactlyInAnyOrderElementsOf(secondPassHistory);
    }

    @Test
    void onEnd() {
        // given
        setup();

        // Inserting record files with HAPI version < 0.37
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
        // Calling onEnd with HAPI version > 0.37
        migration.onEnd(RecordFile.builder()
                .consensusStart(1568415600193620009L)
                .consensusEnd(1568415600193620010L)
                .hapiVersionMajor(0)
                .hapiVersionMinor(37)
                .hapiVersionPatch(1)
                .build());

        // then
        assertContractResultSender();
    }

    @Test
    void onEndHapiVersionNotMatched() {
        // given
        // Token allowance and token allowance history entries that have the incorrect owner
        var tokenAllowancePairIncorrectOwner =
                generateTokenAllowance(CONTRACT_RESULT_SENDER_ID, INCORRECT_OWNER_ACCOUNT_ID);

        // A token allowance that has no contract result, but has primary key values that will end up matching those of
        // a migrated token allowance.
        var preMigrationTokenAllowance = getCollidedTokenAllowance(OWNER_ACCOUNT_ID, 1676546171829734003L);

        // A token allowance that has a contract result, once migrated it will have the same primary key fields as the
        // above token allowance.

        var tokenAllowanceCollided =
                getCollidedTokenAllowance(OWNER_PRE_MIGRATION, CONTRACT_RESULT_CONSENSUS_TIMESTAMP);

        // The contract result for the collided token allowance
        persistContractResultForCollidedTokenAllowance(
                OWNER_ACCOUNT_ID, OWNER_PRE_MIGRATION, CONTRACT_RESULT_CONSENSUS_TIMESTAMP);

        domainBuilder
                .recordFile()
                .customize(r -> r.hapiVersionMajor(0).hapiVersionMinor(37).hapiVersionPatch(0))
                .persist();

        // when
        migration.onEnd(RecordFile.builder()
                .consensusStart(1568415600193620000L)
                .consensusEnd(1568528100472477002L)
                .hapiVersionMajor(0)
                .hapiVersionMinor(37)
                .hapiVersionPatch(1)
                .build());

        // then
        var expected = new ArrayList<>(List.of(tokenAllowancePairIncorrectOwner.getLeft()));
        // The primary key will not be updated since migration is not run
        expected.add(tokenAllowanceCollided);
        expected.add(preMigrationTokenAllowance);
        assertThat(tokenAllowanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);

        // The history of the token allowance should also be updated with the corrected owner
        var expectedHistory = new ArrayList<>(tokenAllowancePairIncorrectOwner.getRight());
        // The owner should be set to the contract result sender id
        assertThat(findHistory(TokenAllowance.class)).containsExactlyInAnyOrderElementsOf(expectedHistory);
    }

    private void setup() {
        // Token allowance and token allowance history entries that have the incorrect owner

        incorrectTokenAllowancePair = generateTokenAllowance(CONTRACT_RESULT_SENDER_ID, INCORRECT_OWNER_ACCOUNT_ID);

        // A token allowance can occur through HAPI and absent a corresponding Contract Result.
        // This token allowance and it's history should be unaffected by the migration
        unaffectedTokenAllowancePair = generateTokenAllowance(null, null);

        // This problem has already been fixed. So there are token allowances with the correct owner.
        // This token allowance and it's history should be unaffected by the migration
        correctTokenAllowancePair =
                generateTokenAllowance(CORRECT_CONTRACT_RESULT_SENDER_ID, CORRECT_CONTRACT_RESULT_SENDER_ID.getId());

        // A token allowance that has no history and the correct owner, it should not be affected by the migration.
        newTokenAllowance = domainBuilder.tokenAllowance().persist();
        domainBuilder
                .contractResult()
                .customize(c -> c.senderId(EntityId.of(0L, 0L, newTokenAllowance.getOwner()))
                        .consensusTimestamp(newTokenAllowance.getTimestampLower()))
                .persist();

        // A token allowance that has no contract result, but has primary key values that will end up matching those of
        // a migrated token allowance.
        tokenAllowancePreMigration = getCollidedTokenAllowance(OWNER_ACCOUNT_ID, 1676546171829734003L);

        // A token allowance that has a contract result, once migrated it will have the same primary key fields as the
        // above token allowance.
        collidedTokenAllowance = getCollidedTokenAllowance(OWNER_PRE_MIGRATION, CONTRACT_RESULT_CONSENSUS_TIMESTAMP);

        // The contract result for the collided token allowance
        persistContractResultForCollidedTokenAllowance(
                OWNER_ACCOUNT_ID, OWNER_PRE_MIGRATION, CONTRACT_RESULT_CONSENSUS_TIMESTAMP);
    }

    private void persistContractResultForCollidedTokenAllowance(
            long ownerAccountId, long ownerPreMigration, long contractResultConsensus) {
        domainBuilder
                .contractResult()
                .customize(c -> c.senderId(EntityId.of(ownerAccountId))
                        .payerAccountId(EntityId.of(ownerPreMigration))
                        .consensusTimestamp(contractResultConsensus))
                .persist();
    }

    private TokenAllowance getCollidedTokenAllowance(long ownerPreMigration, long contractResultConsensus) {
        return domainBuilder
                .tokenAllowance()
                .customize(t -> t.owner(ownerPreMigration)
                        .payerAccountId(EntityId.of(ownerPreMigration))
                        .spender(SPENDER)
                        .timestampRange(Range.atLeast(contractResultConsensus))
                        .tokenId(TOKEN_ID))
                .persist();
    }

    private void assertContractResultSender() {
        var expected = new ArrayList<>(List.of(incorrectTokenAllowancePair.getLeft()));
        // The owner should be set to the contract result sender id
        expected.forEach(t -> t.setOwner(CONTRACT_RESULT_SENDER_ID.getId()));
        expected.add(unaffectedTokenAllowancePair.getLeft());
        expected.add(correctTokenAllowancePair.getLeft());
        expected.add(newTokenAllowance);
        collidedTokenAllowance.setOwner(OWNER_ACCOUNT_ID);
        expected.add(collidedTokenAllowance);
        assertThat(tokenAllowanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);

        // The history of the token allowance should also be updated with the corrected owner
        var expectedHistory = new ArrayList<>(incorrectTokenAllowancePair.getRight());
        // The owner should be set to the contract result sender id
        expectedHistory.forEach(t -> t.setOwner(CONTRACT_RESULT_SENDER_ID.getId()));
        expectedHistory.addAll(unaffectedTokenAllowancePair.getRight());
        expectedHistory.addAll(correctTokenAllowancePair.getRight());
        tokenAllowancePreMigration.setTimestampUpper(CONTRACT_RESULT_CONSENSUS_TIMESTAMP);
        expectedHistory.add(tokenAllowancePreMigration);
        assertThat(findHistory(TokenAllowance.class)).containsExactlyInAnyOrderElementsOf(expectedHistory);
    }

    /**
     * Creates a token allowance and two historical token allowances to populate token_allowance and
     * token_allowance_history
     *
     * @param contractResultSenderId
     * @param ownerAccountId
     * @return the current token allowance and the historical token allowances
     */
    private Pair<TokenAllowance, List<TokenAllowance>> generateTokenAllowance(
            EntityId contractResultSenderId, Long ownerAccountId) {
        var builder = domainBuilder.tokenAllowance();
        if (contractResultSenderId != null) {
            builder.customize(t -> t.owner(ownerAccountId));
        }
        var currentTokenAllowance = builder.persist();
        var range = currentTokenAllowance.getTimestampRange();
        var rangeUpdate1 = Range.closedOpen(range.lowerEndpoint() - 2, range.lowerEndpoint() - 1);
        var rangeUpdate2 = Range.closedOpen(range.lowerEndpoint() - 1, range.lowerEndpoint());

        var update1 = builder.customize(t -> t.amount(999L).amountGranted(999L).timestampRange(rangeUpdate1))
                .get();
        var update2 = builder.customize(t -> t.amount(0).amountGranted(0L).timestampRange(rangeUpdate2))
                .get();
        saveHistory(update1);
        saveHistory(update2);

        if (contractResultSenderId != null) {
            var contractResult = domainBuilder.contractResult();
            for (var tokenAllowance : List.of(currentTokenAllowance, update1, update2)) {
                contractResult
                        .customize(c -> c.senderId(contractResultSenderId)
                                .consensusTimestamp(tokenAllowance.getTimestampLower()))
                        .persist();
            }
        }

        return Pair.of(currentTokenAllowance, List.of(update1, update2));
    }

    private void saveHistory(TokenAllowance tokenAllowance) {
        domainBuilder
                .tokenAllowanceHistory()
                .customize(c -> c.amount(tokenAllowance.getAmount())
                        .amountGranted(tokenAllowance.getAmountGranted())
                        .owner(tokenAllowance.getOwner())
                        .payerAccountId(tokenAllowance.getPayerAccountId())
                        .spender(tokenAllowance.getSpender())
                        .timestampRange(tokenAllowance.getTimestampRange())
                        .tokenId(tokenAllowance.getTokenId()))
                .persist();
    }
}
