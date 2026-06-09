// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.common.domain.entity.EntityType.ACCOUNT;
import static org.hiero.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.hiero.mirror.common.domain.entity.EntityType.TOPIC;
import static org.hiero.mirror.common.util.DomainUtils.TINYBARS_IN_ONE_HBAR;

import com.google.common.collect.Range;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.hiero.mirror.common.domain.addressbook.NodeStake;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.balance.AccountBalance.Id;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityStake;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.util.Utility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionOperations;

@RequiredArgsConstructor
final class EntityStakeRepositoryTest extends ImporterIntegrationTest {

    private static final String[] ENTITY_STATE_START_FIELDS =
            new String[] {"balance", "id", "stakedAccountId", "stakedNodeId", "stakePeriodStart"};
    private static final long ONE_MONTH = Duration.ofDays(31).toNanos();
    private static final RowMapper<Entity> ROW_MAPPER = rowMapper(Entity.class);

    private final EntityRepository entityRepository;
    private final EntityStakeRepository entityStakeRepository;
    private final TransactionOperations transactionOperations;

    private long stakingRewardAccountId;

    @BeforeEach
    void setup() {
        stakingRewardAccountId = systemEntity.stakingRewardAccount().getId();
    }

    @Test
    void createEntityStateStart() {
        // given
        long epochDay = 1000L;
        long nodeStakeTimestamp = DomainUtils.convertToNanosMax(TestUtils.asStartOfEpochDay(epochDay + 1)) + 1000L;
        long previousNodeStakeTimestamp = DomainUtils.convertToNanosMax(TestUtils.asStartOfEpochDay(epochDay)) + 1000L;

        domainBuilder
                .nodeStake()
                .customize(
                        ns -> ns.consensusTimestamp(previousNodeStakeTimestamp).epochDay(epochDay - 1))
                .persist();
        domainBuilder
                .nodeStake()
                .customize(ns -> ns.consensusTimestamp(nodeStakeTimestamp).epochDay(epochDay))
                .persist();

        var stakingRewardAccount = domainBuilder
                .entity(stakingRewardAccountId, nodeStakeTimestamp - 10)
                .persist();
        var treasury = domainBuilder
                .entity(systemEntity.treasuryAccount(), nodeStakeTimestamp - 20)
                .persist();
        var account1 = domainBuilder
                .entity()
                .customize(e -> e.stakedNodeId(1L).timestampRange(Range.atLeast(nodeStakeTimestamp - 1)))
                .persist();
        var account2 = domainBuilder
                .entity()
                .customize(e -> e.deleted(null).stakedNodeId(1L).timestampRange(Range.atLeast(nodeStakeTimestamp - 2)))
                .persist();
        // account3 is valid after the node stake update timestamp
        var account3 = domainBuilder
                .entity()
                .customize(e -> e.stakedNodeId(3L).timestampRange(Range.atLeast(nodeStakeTimestamp + 1)))
                .persist();
        // history row for account3
        var account3History = domainBuilder
                .entityHistory(account3.toEntityId())
                .customize(e -> e.stakedNodeId(3L)
                        .timestampRange(Range.closedOpen(nodeStakeTimestamp - 10, nodeStakeTimestamp + 1)))
                .persist();
        // deleted account will not appear in entity_state_start
        var account4 = domainBuilder
                .entity()
                .customize(e -> e.deleted(true).stakedNodeId(1L).timestampRange(Range.atLeast(nodeStakeTimestamp - 3)))
                .persist(); // deleted
        // entity created after node stake timestamp will not appear in entity_state_start
        domainBuilder
                .entity()
                .customize(e -> e.stakedNodeId(1L).timestampRange(Range.atLeast(nodeStakeTimestamp + 1)))
                .persist();
        var contract = domainBuilder
                .entity()
                .customize(e -> e.stakedAccountId(null)
                        .stakedNodeId(1L)
                        .stakePeriodStart(null)
                        .timestampRange(Range.atLeast(nodeStakeTimestamp - 4))
                        .type(CONTRACT))
                .persist();
        domainBuilder
                .entity()
                .customize(e -> e.type(TOPIC).timestampRange(Range.atLeast(nodeStakeTimestamp - 5)))
                .persist();

        long latestBalanceTimestamp = nodeStakeTimestamp - 100;
        var balanceTimestamp = new AtomicLong(latestBalanceTimestamp);
        domainBuilder
                .accountBalance()
                .customize(ab ->
                        ab.balance(50000L).id(new AccountBalance.Id(balanceTimestamp.get(), treasury.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(
                        ab -> ab.balance(100L).id(new AccountBalance.Id(balanceTimestamp.get(), account1.toEntityId())))
                .persist();
        // Balance info at the beginning of the month, note balance info of account2, account4, and contract is deduped
        balanceTimestamp.addAndGet(-ONE_MONTH + 1);
        domainBuilder
                .accountBalance()
                .customize(ab ->
                        ab.balance(50000L).id(new AccountBalance.Id(balanceTimestamp.get(), treasury.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(
                        ab -> ab.balance(80L).id(new AccountBalance.Id(balanceTimestamp.get(), account1.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(
                        ab -> ab.balance(200L).id(new AccountBalance.Id(balanceTimestamp.get(), account2.toEntityId())))
                .persist();

        domainBuilder
                .accountBalance()
                .customize(
                        ab -> ab.balance(400L).id(new AccountBalance.Id(balanceTimestamp.get(), account4.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(
                        ab -> ab.balance(500L).id(new AccountBalance.Id(balanceTimestamp.get(), contract.toEntityId())))
                .persist();
        // Last balance snapshot in the previous month, note the timestamp is chosen to test one-off issue
        balanceTimestamp.addAndGet(-1);
        domainBuilder
                .accountBalance()
                .customize(ab ->
                        ab.balance(50000L).id(new AccountBalance.Id(balanceTimestamp.get(), treasury.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(
                        ab -> ab.balance(199L).id(new AccountBalance.Id(balanceTimestamp.get(), account2.toEntityId())))
                .persist();

        persistCryptoTransfer(20L, latestBalanceTimestamp, account1.getId());
        persistCryptoTransfer(30L, latestBalanceTimestamp + 1, account1.getId());
        persistCryptoTransfer(-10L, latestBalanceTimestamp + 54, account2.getId());
        // account3 is created after the account balance snapshot timestamp
        persistCryptoTransfer(123L, account3History.getTimestampLower(), account3.getId());

        var expectedAccount1 =
                account1.toBuilder().balance(130L).stakedAccountId(0L).build();
        var expectedAccount2 =
                account2.toBuilder().balance(190L).stakedAccountId(0L).build();
        var expectedAccount3 = account3.toBuilder()
                .balance(123L)
                .stakedAccountId(0L)
                .stakedNodeId(3L)
                .build();
        var expectedContract = contract.toBuilder()
                .balance(500L)
                .stakedAccountId(0L)
                .stakedNodeId(1L)
                .stakePeriodStart(-1L)
                .build();
        var expectedStackingRewardAccount = stakingRewardAccount.toBuilder()
                .balance(0L)
                .stakedAccountId(0L)
                .stakedNodeId(-1L)
                .stakePeriodStart(-1L)
                .build();

        transactionOperations.executeWithoutResult(s -> {
            // when
            entityStakeRepository.createEntityStateStart(stakingRewardAccountId);

            // then
            assertEntityStartStart(List.of(
                    expectedAccount1,
                    expectedAccount2,
                    expectedAccount3,
                    expectedContract,
                    expectedStackingRewardAccount));
        });

        // given
        expectedAccount1.setDeclineReward(true);
        expectedAccount2.setStakedAccountId(account1.getId());
        expectedAccount2.setStakePeriodStart(10L);
        contract.setDeleted(true);
        entityRepository.saveAll(List.of(expectedAccount1, expectedAccount2, contract));

        transactionOperations.executeWithoutResult(s -> {
            // when
            entityStakeRepository.createEntityStateStart(stakingRewardAccountId);

            // then
            assertEntityStartStart(List.of(expectedAccount2, expectedAccount3, expectedStackingRewardAccount));
        });
    }

    @Test
    void createEntityStateStartWhenEmptyEntity() {
        // given
        long epochDay = 1000L;
        long timestamp = DomainUtils.convertToNanosMax(TestUtils.asStartOfEpochDay(epochDay + 1)) + 1000L;
        domainBuilder
                .nodeStake()
                .customize(n -> n.consensusTimestamp(timestamp).epochDay(epochDay))
                .persist();
        long balanceTimestamp = timestamp - 1000L;
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(balanceTimestamp, systemEntity.treasuryAccount())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(balanceTimestamp, domainBuilder.entityId())))
                .persist();

        transactionOperations.executeWithoutResult(s -> {
            // when
            entityStakeRepository.createEntityStateStart(stakingRewardAccountId);

            // then
            assertEntityStartStart(Collections.emptyList());
        });
    }

    @Test
    void createEntityStateStartWhenEmptyAccountBalance() {
        // given
        long epochDay = 1000L;
        long timestamp = DomainUtils.convertToNanosMax(TestUtils.asStartOfEpochDay(epochDay + 1)) + 1000L;
        domainBuilder
                .nodeStake()
                .customize(ns -> ns.consensusTimestamp(timestamp).epochDay(epochDay))
                .persist();
        domainBuilder.entity(stakingRewardAccountId, timestamp - 5000L).persist();

        transactionOperations.executeWithoutResult(s -> {
            // when
            entityStakeRepository.createEntityStateStart(stakingRewardAccountId);

            // then
            assertEntityStartStart(Collections.emptyList());
        });
    }

    @Test
    void createEntityStateStartWhenEmptyNodeStake() {
        // given
        long balanceTimestamp = 1_000_000_000L;
        var account = domainBuilder
                .entity()
                .customize(e -> e.timestampRange(Range.atLeast(balanceTimestamp - 1000L)))
                .persist();
        var treasury = domainBuilder
                .entity(systemEntity.treasuryAccount(), balanceTimestamp - 9000)
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(100L).id(new AccountBalance.Id(balanceTimestamp, account.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(200L).id(new AccountBalance.Id(balanceTimestamp, treasury.toEntityId())))
                .persist();

        transactionOperations.executeWithoutResult(s -> {
            // when
            entityStakeRepository.createEntityStateStart(stakingRewardAccountId);

            // then
            assertEntityStartStart(Collections.emptyList());
        });
    }

    @Test
    void createEntityStateStartWhenNonEmptyEntityStakeAndCatchup() {
        // given
        long epochDay = 1000L;
        long nodeStakeTimestamp = DomainUtils.convertToNanosMax(TestUtils.asStartOfEpochDay(epochDay + 1)) + 1000L;
        long nextNodeStakeTimestamp = DomainUtils.convertToNanosMax(TestUtils.asStartOfEpochDay(epochDay + 2)) + 600L;
        long previousNodeStakeTimestamp = DomainUtils.convertToNanosMax(TestUtils.asStartOfEpochDay(epochDay)) + 700L;

        // Alice changed her staking settings after previousNodeStakeTimestamp, then she changed it again after
        // nextNodeStakeTimestamp. In entity state start, alice's stake settings should come from aliceHistory2
        var stakingRewardAccount = domainBuilder
                .entity(stakingRewardAccountId, previousNodeStakeTimestamp - 8000)
                .persist();
        var treasury = domainBuilder
                .entity(systemEntity.treasuryAccount(), previousNodeStakeTimestamp - 9000)
                .customize(e -> e.stakedNodeId(1L))
                .persist();
        var aliceHistory1 = domainBuilder
                .entityHistory()
                .customize(e -> e.stakedNodeId(0L)
                        .stakePeriodStart(epochDay - 1)
                        .timestampRange(
                                Range.closedOpen(previousNodeStakeTimestamp - 900L, previousNodeStakeTimestamp + 200L)))
                .persist();
        var aliceEntityId = aliceHistory1.toEntityId();
        var aliceHistory2 = domainBuilder
                .entityHistory(aliceEntityId)
                .customize(e -> e.stakedAccountId(domainBuilder.id())
                        .stakePeriodStart(epochDay)
                        .timestampRange(Range.closedOpen(aliceHistory1.getTimestampUpper(), nodeStakeTimestamp + 300L)))
                .persist();
        var alice = domainBuilder
                .entity()
                .customize(e -> e.id(aliceHistory2.getId())
                        .num(aliceHistory2.getNum())
                        .stakedAccountId(domainBuilder.id())
                        .timestampRange(Range.atLeast(aliceHistory2.getTimestampUpper())))
                .persist();

        // Account 800's current end stake period is epochDay - 1 while the latest node stake's epoch day is
        // epochDay + 1, thus entity stake is two staking period behind
        domainBuilder
                .entityStake()
                .customize(es -> es.id(stakingRewardAccountId)
                        .endStakePeriod(epochDay - 1)
                        .timestampRange(Range.atLeast(previousNodeStakeTimestamp)))
                .persist();
        domainBuilder
                .nodeStake()
                .customize(ns -> ns.consensusTimestamp(previousNodeStakeTimestamp)
                        .epochDay(epochDay - 1)
                        .nodeId(0))
                .persist();
        domainBuilder
                .nodeStake()
                .customize(ns -> ns.consensusTimestamp(nodeStakeTimestamp)
                        .epochDay(epochDay)
                        .nodeId(0))
                .persist();
        domainBuilder
                .nodeStake()
                .customize(ns -> ns.consensusTimestamp(nextNodeStakeTimestamp)
                        .epochDay(epochDay + 1)
                        .nodeId(0))
                .persist();

        // Account balance and crypto transfer before nodeStakeTimestamp
        long balanceTimestamp = nodeStakeTimestamp - 500;
        long previousBalanceTimestamp = balanceTimestamp - 500;
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(5000).id(new Id(balanceTimestamp, treasury.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(5000).id(new Id(previousBalanceTimestamp, treasury.toEntityId())))
                .persist();
        // Deduped balance info
        domainBuilder
                .accountBalance()
                .customize(
                        ab -> ab.balance(1000).id(new Id(previousBalanceTimestamp, stakingRewardAccount.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(1500).id(new Id(previousBalanceTimestamp, aliceEntityId)))
                .persist();
        persistCryptoTransfer(150, balanceTimestamp + 10, stakingRewardAccountId);

        // Account balance before nextNodeStakeTimestamp
        long accountBalanceTimestampBeforeNextNodeStake = nextNodeStakeTimestamp - 700;
        domainBuilder
                .accountBalance()
                .customize(ab ->
                        ab.balance(5000).id(new Id(accountBalanceTimestampBeforeNextNodeStake, treasury.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(5000)
                        .id(new Id(accountBalanceTimestampBeforeNextNodeStake - 100, treasury.toEntityId())))
                .persist();
        // Deduped balance info
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(2000)
                        .id(new Id(
                                accountBalanceTimestampBeforeNextNodeStake - 100, stakingRewardAccount.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab ->
                        ab.balance(1700).id(new Id(accountBalanceTimestampBeforeNextNodeStake - 100, aliceEntityId)))
                .persist();

        var expectedAlice = alice.toBuilder()
                .balance(1500L)
                .stakedAccountId(aliceHistory2.getStakedAccountId())
                .stakePeriodStart(aliceHistory2.getStakePeriodStart())
                .build();
        var expectedStakingRewardAccount = domainBuilder
                .entity()
                .customize(e -> e.balance(1150L)
                        .id(stakingRewardAccountId)
                        .stakedAccountId(0L)
                        .stakedNodeId(-1L)
                        .stakePeriodStart(-1L))
                .get();
        var expectedTreasury = domainBuilder
                .entity()
                .customize(e -> e.balance(5000L)
                        .id(systemEntity.treasuryAccount().getId())
                        .stakedAccountId(0L)
                        .stakedNodeId(1L)
                        .stakePeriodStart(-1L))
                .get();

        transactionOperations.executeWithoutResult(s -> {
            // when
            entityStakeRepository.createEntityStateStart(stakingRewardAccountId);

            // then
            assertEntityStartStart(List.of(expectedAlice, expectedStakingRewardAccount, expectedTreasury));
        });
    }

    @Test
    void getEndStakePeriod() {
        assertThat(entityStakeRepository.getEndStakePeriod(stakingRewardAccountId))
                .isEmpty();

        long endStakePeriod = domainBuilder.number();
        domainBuilder
                .entityStake()
                .customize(es -> es.endStakePeriod(endStakePeriod).id(stakingRewardAccountId))
                .persist();
        assertThat(entityStakeRepository.getEndStakePeriod(stakingRewardAccountId))
                .contains(endStakePeriod);
    }

    @SneakyThrows
    @Test
    @Timeout(5)
    void lockFormConcurrentUpdates() {
        // given
        var locked = new AtomicBoolean(false);
        var semaphore = new Semaphore(0);
        var other = new Thread(() -> transactionOperations.executeWithoutResult(s -> {
            entityStakeRepository.lockFromConcurrentUpdates();
            locked.set(true);
            try {
                // hold the table lock
                semaphore.acquire();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }));
        other.start();

        while (true) {
            // busy wait until the table lock is acquired in other thread
            if (locked.get()) {
                break;
            }
        }

        // when, then
        assertThatThrownBy(entityStakeRepository::lockFromConcurrentUpdates).isInstanceOf(Exception.class);

        // cleanup
        semaphore.release();
        other.join();
    }

    @ParameterizedTest
    @CsvSource({
        ",,true,true", // empty node_stake, empty entity_stake, has staking reward account
        ",,false,true", // empty node_stake, empty entity_stake, no staking reward account
        ",5,true,true", // empty node_stake, non-empty entity_stake, has staking reward account
        ",5,false,true", // empty node_stake, non-empty entity_stake, no staking reward account
        "5,5,true,true", // entity_stake is up-to-date, has staking reward account
        "5,5,false,true", // entity_stake is up-to-date, no staking reward account
        "5,,true,false", // non-empty node_stake, empty entity_stake, has staking reward account
        "5,,false,true", // non-empty node_stake, empty entity_stake, no staking reward account
        "5,4,true,false", // node_stake is ahead of entity_stake, has staking reward account
        "5,4,false,true", // node_stake is ahead of entity_stake, no staking reward account
    })
    void updated(Long epochDay, Long endStakePeriod, boolean hasStakingRewardAccount, boolean expected) {
        // given
        if (epochDay != null) {
            domainBuilder.nodeStake().customize(n -> n.epochDay(epochDay - 1)).persist();
            domainBuilder.nodeStake().customize(n -> n.epochDay(epochDay)).persist(); // with a later timestamp
        }

        if (endStakePeriod != null) {
            domainBuilder
                    .entityStake()
                    .customize(e -> e.id(stakingRewardAccountId).endStakePeriod(endStakePeriod))
                    .persist();
            domainBuilder
                    .entityStake()
                    .customize(e -> e.id(801L).endStakePeriod(endStakePeriod - 1))
                    .persist();
        }

        if (hasStakingRewardAccount) {
            domainBuilder
                    .entity(stakingRewardAccountId, domainBuilder.timestamp())
                    .persist();
        }

        // when
        boolean actual = entityStakeRepository.updated(stakingRewardAccountId);

        // then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void updateEntityStake() {
        // given
        var entity1 = domainBuilder
                .entity()
                .customize(e -> e.stakedAccountId(null).stakedNodeId(1L))
                .persist();
        var entity2 = domainBuilder
                .entity()
                .customize(e -> e.declineReward(true).stakedAccountId(entity1.getId()))
                .persist();
        var entity3 = domainBuilder
                .entity()
                .customize(e ->
                        e.stakedAccountId(entity1.getId()).stakedNodeId(null).type(CONTRACT))
                .persist();
        var entity4 = domainBuilder
                .entity()
                .customize(e -> e.deleted(true).stakedAccountId(entity1.getId()))
                .persist();
        var entity5 = domainBuilder
                .entity()
                .customize(e -> e.stakedAccountId(entity3.getId()))
                .persist();
        var entity6 = domainBuilder
                .entity()
                .customize(e -> e.stakedAccountId(domainBuilder.id()))
                .persist();
        domainBuilder.topicEntity().persist();
        var entity8 = domainBuilder
                .entity()
                .customize(e -> e.stakedAccountId(entity6.getId()))
                .persist();
        long entityId9 = entity8.getId() + 1;
        long entityId10 = entityId9 + 1;
        var entity9 = domainBuilder
                .entity(entityId9, domainBuilder.timestamp())
                .customize(e -> e.stakedAccountId(entityId10))
                .persist();
        var entity10 = domainBuilder
                .entity(entityId10, domainBuilder.timestamp())
                .customize(e -> e.stakedAccountId(entityId9))
                .persist();
        var stakingRewardAccount = domainBuilder
                .entity(stakingRewardAccountId, domainBuilder.timestamp())
                .persist();
        long timestamp = domainBuilder.timestamp();
        var nodeStake = domainBuilder
                .nodeStake()
                .customize(ns -> ns.consensusTimestamp(timestamp).nodeId(1L).rewardRate(0L))
                .persist();
        // account balance
        long balanceTimestamp = nodeStake.getConsensusTimestamp() - 1000L;
        long previousBalanceTimestamp = balanceTimestamp - 1000L;
        domainBuilder
                .accountBalance()
                .customize(ab ->
                        ab.balance(5000L).id(new AccountBalance.Id(balanceTimestamp, systemEntity.treasuryAccount())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(100L).id(new AccountBalance.Id(balanceTimestamp, entity1.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(200L).id(new AccountBalance.Id(balanceTimestamp, entity2.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(300L).id(new AccountBalance.Id(balanceTimestamp, entity3.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(400L).id(new AccountBalance.Id(balanceTimestamp, entity4.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(500L).id(new AccountBalance.Id(balanceTimestamp, entity5.toEntityId())))
                .persist();
        // Deduped
        domainBuilder
                .accountBalance()
                .customize(ab ->
                        ab.balance(600L).id(new AccountBalance.Id(previousBalanceTimestamp, entity6.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(stakingRewardAccountId)
                        .id(new AccountBalance.Id(previousBalanceTimestamp, entity8.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab ->
                        ab.balance(900L).id(new AccountBalance.Id(previousBalanceTimestamp, entity9.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab ->
                        ab.balance(1000L).id(new AccountBalance.Id(previousBalanceTimestamp, entity10.toEntityId())))
                .persist();

        // existing entity stake, note entity4 has been deleted, its existing entity stake will no longer update
        var existingEntityStake1 = domainBuilder
                .entityStake()
                .customize(es -> es.id(entity1.getId())
                        .timestampRange(Range.atLeast(TestUtils.plus(
                                nodeStake.getConsensusTimestamp(),
                                Duration.ofDays(1).negated()))))
                .persist();
        var existingEntityStake4 = domainBuilder
                .entityStake()
                .customize(es -> es.endStakePeriod(nodeStake.getEpochDay() - 1).id(entity4.getId()))
                .persist();
        var expectedEntityStakes = List.of(
                fromEntity(entity1, nodeStake, 500L, 600L),
                existingEntityStake4,
                fromEntity(stakingRewardAccount, nodeStake, 0L, 0L));
        var entityStake1History = existingEntityStake1.toBuilder().build();
        entityStake1History.setTimestampUpper(nodeStake.getConsensusTimestamp());

        // when
        transactionOperations.executeWithoutResult(s -> {
            entityStakeRepository.createEntityStateStart(stakingRewardAccountId);
            entityStakeRepository.updateEntityStake(stakingRewardAccountId);
        });

        // then
        assertThat(entityStakeRepository.findAll())
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("pendingReward")
                .containsExactlyInAnyOrderElementsOf(expectedEntityStakes);
        assertThat(findHistory(EntityStake.class)).containsExactly(entityStake1History);
    }

    @Test
    void updateEntityStakeForNewEntities() {
        // given
        var declineRewardAccount =
                domainBuilder.entity().customize(e -> e.declineReward(true)).persist();
        var contract = domainBuilder
                .entity()
                .customize(e -> e.stakedNodeId(2L).type(CONTRACT))
                .persist();
        domainBuilder.entity().customize(e -> e.deleted(true)).persist();
        domainBuilder.topicEntity().persist();
        var nodeStake = domainBuilder.nodeStake().persist();
        var stakingRewardAccount = domainBuilder
                .entity(stakingRewardAccountId, nodeStake.getConsensusTimestamp() - 10)
                .persist();
        long balanceTimestamp = nodeStake.getConsensusTimestamp() - 1000L;
        long previousBalanceTimestamp = balanceTimestamp - 1000L;
        // Treasury account balance
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(balanceTimestamp, systemEntity.treasuryAccount())))
                .persist();
        var account = domainBuilder
                .entity(domainBuilder.id(), nodeStake.getConsensusTimestamp() - 20)
                .customize(e -> e.stakedNodeId(1L))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(500L).id(new AccountBalance.Id(balanceTimestamp, account.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab ->
                        ab.balance(5000L).id(new AccountBalance.Id(previousBalanceTimestamp, account.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab ->
                        ab.balance(0L).id(new AccountBalance.Id(balanceTimestamp, declineRewardAccount.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(
                        ab -> ab.balance(5L).id(new AccountBalance.Id(previousBalanceTimestamp, contract.toEntityId())))
                .persist();
        var expectedEntityStakes = List.of(
                fromEntity(contract, nodeStake, 0L, 5L),
                fromEntity(stakingRewardAccount, nodeStake, 0L, 0L),
                fromEntity(account, nodeStake, 0L, 500L));

        // when
        transactionOperations.executeWithoutResult(s -> {
            entityStakeRepository.createEntityStateStart(stakingRewardAccountId);
            entityStakeRepository.updateEntityStake(stakingRewardAccountId);
        });

        // then
        assertThat(entityStakeRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedEntityStakes);
        assertThat(findHistory(EntityStake.class)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "0, false, 1, -1, 15090000000, true, 1500", // The first period the account earns reward
        "20, false, 1, -2, 15090000000, true, 1520", // Accumulate the newly earned reward
        "20, false, 1, -2, 15090000000, false, 1520", // Accumulate the newly earned reward, contract
        "20, false, 1, 0, 15090000000, false, 0", // The account just started a new staking period, reset to 0
        "0, false, 1, -1, 0, false, 0", // The account has 0 stake total start, reward should be 0
        "0, false, 2, -1, 15090000000, true, 0", // The node reward rate is 0, reward should be 0
        "20, false, 3, 0, 15090000000, true, 20", // No node stake for node 3, the pending reward keeps the same
        "20, false, -1, , 15090000000, true, 20", // Not currently staked to a node, but staked to a node in the
        // previous period
        "0, true, 1, , 15090000000, true, 0", // Decline reward start is true
    })
    void updateEntityStakePendingReward(
            long currentPendingReward,
            boolean declineReward,
            long stakedNodeIdStart,
            Long stakePeriodStartOffset,
            long stakeTotalStart,
            boolean isAccountOrContract,
            long expectedPendingReward) {
        // given
        long nodeStakeEpochDay = 200L;
        long previousNodeStakeEpochDay = nodeStakeEpochDay - 1;
        long nodeStakeTimestamp =
                DomainUtils.convertToNanosMax(TestUtils.asStartOfEpochDay(nodeStakeEpochDay + 1)) + 500;
        long stakePeriodStart = stakePeriodStartOffset != null ? nodeStakeEpochDay + stakePeriodStartOffset : -1;
        long entityLowerTimestamp =
                TestUtils.plus(nodeStakeTimestamp, Duration.ofHours(25).negated());
        var entity = domainBuilder
                .entity()
                .customize(e -> e.declineReward(declineReward)
                        .stakedNodeId(stakedNodeIdStart)
                        .stakePeriodStart(stakePeriodStart)
                        .timestampRange(Range.atLeast(entityLowerTimestamp))
                        .type(isAccountOrContract ? ACCOUNT : CONTRACT))
                .persist();
        long nodeStakeLowerTimestamp =
                TestUtils.plus(nodeStakeTimestamp, Duration.ofDays(1).negated());
        var existingEntityStake = domainBuilder
                .entityStake()
                .customize(es -> es.endStakePeriod(previousNodeStakeEpochDay)
                        .id(entity.getId())
                        .pendingReward(currentPendingReward)
                        .stakedNodeIdStart(stakedNodeIdStart)
                        .stakeTotalStart(stakeTotalStart)
                        .timestampRange(Range.atLeast(nodeStakeLowerTimestamp)))
                .persist();
        // existing entity stake for staking reward account
        domainBuilder
                .entityStake()
                .customize(es -> es.id(stakingRewardAccountId)
                        .endStakePeriod(previousNodeStakeEpochDay)
                        .timestampRange(Range.atLeast(nodeStakeLowerTimestamp)))
                .persist();
        domainBuilder
                .nodeStake()
                .customize(ns -> ns.consensusTimestamp(nodeStakeTimestamp)
                        .epochDay(nodeStakeEpochDay)
                        .nodeId(1L)
                        .rewardRate(10L))
                .persist();
        domainBuilder
                .nodeStake()
                .customize(ns -> ns.consensusTimestamp(nodeStakeTimestamp)
                        .epochDay(nodeStakeEpochDay)
                        .nodeId(2L)
                        .rewardRate(0L))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(
                        ab -> ab.id(new AccountBalance.Id(nodeStakeTimestamp - 1000, systemEntity.treasuryAccount())))
                .persist();
        // The following two are old NodeStake, which shouldn't be used in pending reward calculation
        domainBuilder
                .nodeStake()
                .customize(ns -> ns.epochDay(previousNodeStakeEpochDay)
                        .consensusTimestamp(nodeStakeTimestamp - 100)
                        .nodeId(1L))
                .persist();
        domainBuilder
                .nodeStake()
                .customize(ns -> ns.epochDay(previousNodeStakeEpochDay)
                        .consensusTimestamp(nodeStakeTimestamp - 100)
                        .nodeId(2L))
                .persist();
        existingEntityStake.setTimestampUpper(nodeStakeTimestamp);

        // when
        transactionOperations.executeWithoutResult(s -> {
            entityStakeRepository.createEntityStateStart(stakingRewardAccountId);
            entityStakeRepository.updateEntityStake(stakingRewardAccountId);
        });

        // then
        var expectedEndStakePeriod =
                (stakedNodeIdStart <= 0 || declineReward) ? previousNodeStakeEpochDay : nodeStakeEpochDay;
        assertThat(entityStakeRepository.findById(entity.getId()))
                .get()
                .returns(expectedEndStakePeriod, EntityStake::getEndStakePeriod)
                .returns(expectedPendingReward, EntityStake::getPendingReward);

        if (expectedEndStakePeriod == nodeStakeEpochDay) {
            assertThat(findHistory(EntityStake.class)).containsExactly(existingEntityStake);
        } else {
            // If the entity stake was not updated, the history row should not be created
            assertThat(findHistory(EntityStake.class)).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void updateEntityStakePendingRewardExceedsMaxNumberOfStakingPeriods(
            final boolean multipleNodeStakeUpdatesOnForfeitedPeriod) {
        // given
        long epochDay = Utility.getEpochDay(domainBuilder.timestamp());
        long forfeitedStakingPeriod = epochDay - 365;
        long nodeStakeTimestamp = DomainUtils.convertToNanosMax(
                TestUtils.asStartOfEpochDay(epochDay + 1).plusNanos(300));
        long previousNodeStakeTimestamp = DomainUtils.convertToNanosMax(
                TestUtils.asStartOfEpochDay(epochDay).plusNanos(200));
        long forfeitedNodeStakeTimestamp = DomainUtils.convertToNanosMax(
                TestUtils.asStartOfEpochDay(forfeitedStakingPeriod + 1).plusNanos(180));
        long entityLowerTimestamp = DomainUtils.convertToNanosMax(
                TestUtils.asStartOfEpochDay(forfeitedStakingPeriod).minusNanos(100));

        var stakingRewardAccount = domainBuilder
                .entity(stakingRewardAccountId, entityLowerTimestamp)
                .persist();
        // forfeitedStakingPeriod is the first period the account has earned staking reward, so the stake period start
        // is forfeitedStakingPeriod - 1
        var account = domainBuilder
                .entity(stakingRewardAccountId + domainBuilder.id(), entityLowerTimestamp + 1)
                .customize(e -> e.stakedNodeId(forfeitedStakingPeriod - 1))
                .persist();
        // The end result is the pending reward will decrease by 100 * stake total start
        domainBuilder
                .nodeStake()
                .customize(ns -> ns.consensusTimestamp(forfeitedNodeStakeTimestamp)
                        .epochDay(forfeitedStakingPeriod)
                        .nodeId(0L)
                        .rewardRate(300))
                .persist();
        if (multipleNodeStakeUpdatesOnForfeitedPeriod) {
            domainBuilder
                    .nodeStake()
                    .customize(ns -> ns.consensusTimestamp(forfeitedNodeStakeTimestamp + 1)
                            .epochDay(forfeitedStakingPeriod)
                            .nodeId(0L)
                            .rewardRate(300))
                    .persist();
        }

        var nodeStake = domainBuilder
                .nodeStake()
                .customize(ns -> ns.consensusTimestamp(nodeStakeTimestamp)
                        .epochDay(epochDay)
                        .nodeId(0L)
                        .rewardRate(200))
                .persist();
        var accountStake = domainBuilder
                .entityStake()
                .customize(es -> es.endStakePeriod(epochDay - 1)
                        .id(account.getId())
                        .pendingReward(200_000_000L)
                        .stakedNodeIdStart(0L)
                        .stakeTotalStart(9_000_000_000L)
                        .timestampRange(Range.atLeast(previousNodeStakeTimestamp)))
                .persist();
        var stakingRewardAccountStake = domainBuilder
                .entityStake()
                .customize(es -> es.endStakePeriod(epochDay - 1)
                        .id(stakingRewardAccountId)
                        .timestampRange(Range.atLeast(previousNodeStakeTimestamp)))
                .persist();
        long balanceTimestamp = nodeStakeTimestamp - 2000;
        long previousBalanceTimestamp = balanceTimestamp - 2000;
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new Id(balanceTimestamp, systemEntity.treasuryAccount())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new Id(previousBalanceTimestamp, systemEntity.treasuryAccount())))
                .persist();
        // Deduped
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(accountStake.getStakeTotalStart())
                        .id(new Id(previousBalanceTimestamp, account.toEntityId())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new Id(previousBalanceTimestamp, stakingRewardAccount.toEntityId())))
                .persist();
        var expectedAccountStake = fromEntity(account, nodeStake, 0, accountStake.getStakeTotalStart());
        expectedAccountStake.setPendingReward(
                accountStake.getPendingReward() - 100 * accountStake.getStakeTotalStart() / TINYBARS_IN_ONE_HBAR);
        var expectedStakingRewardAccountStake = fromEntity(stakingRewardAccount, nodeStake, 0, 0);
        // history rows
        accountStake.setTimestampUpper(nodeStakeTimestamp);
        stakingRewardAccountStake.setTimestampUpper(nodeStakeTimestamp);

        // when
        transactionOperations.executeWithoutResult(s -> {
            entityStakeRepository.createEntityStateStart(stakingRewardAccountId);
            entityStakeRepository.updateEntityStake(stakingRewardAccountId);
        });

        // then
        assertThat(entityStakeRepository.findAll())
                .containsExactlyInAnyOrder(expectedAccountStake, expectedStakingRewardAccountStake);
        assertThat(findHistory(EntityStake.class)).containsExactlyInAnyOrder(accountStake, stakingRewardAccountStake);
    }

    @Test
    void save() {
        var entityStake = domainBuilder.entityStake().get();
        entityStakeRepository.save(entityStake);
        assertThat(entityStakeRepository.findById(entityStake.getId())).contains(entityStake);
    }

    private void assertEntityStartStart(List<Entity> expected) {
        assertThat(jdbcOperations.query("select * from entity_state_start", ROW_MAPPER))
                .usingRecursiveFieldByFieldElementComparatorOnFields(ENTITY_STATE_START_FIELDS)
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    private EntityStake fromEntity(Entity entity, NodeStake nodeStake, long stakedToMe, long stakeTotalStart) {
        return EntityStake.builder()
                .endStakePeriod(nodeStake.getEpochDay())
                .id(entity.getId())
                .stakedNodeIdStart(Optional.ofNullable(entity.getStakedNodeId()).orElse(-1L))
                .stakedToMe(stakedToMe)
                .stakeTotalStart(stakeTotalStart)
                .timestampRange(Range.atLeast(nodeStake.getConsensusTimestamp()))
                .build();
    }

    private void persistCryptoTransfer(long amount, long consensusTimestamp, long entityId) {
        domainBuilder
                .cryptoTransfer()
                .customize(ct ->
                        ct.amount(amount).consensusTimestamp(consensusTimestamp).entityId(entityId))
                .persist();
    }
}
