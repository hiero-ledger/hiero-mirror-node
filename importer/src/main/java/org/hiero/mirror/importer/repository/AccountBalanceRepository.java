// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import java.util.List;
import java.util.Optional;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface AccountBalanceRepository
        extends BalanceSnapshotRepository, CrudRepository<AccountBalance, AccountBalance.Id> {

    @Modifying
    @Override
    @Query(
            nativeQuery = true,
            value =
                    """
                    insert into account_balance (account_id, balance, consensus_timestamp)
                    select id, balance, :consensusTimestamp
                    from entity
                    where balance is not null and
                      (deleted is not true or balance_timestamp > (
                          select coalesce(max(consensus_timestamp), 0)
                          from account_balance
                          where account_id = :treasuryAccountId and consensus_timestamp > :consensusTimestamp - 2592000000000000
                        ))
                    order by id
                    """)
    @Transactional(propagation = Propagation.MANDATORY)
    int balanceSnapshot(long consensusTimestamp, long treasuryAccountId);

    @Override
    @Modifying
    @Query(
            nativeQuery = true,
            value =
                    """
                    insert into account_balance (account_id, balance, consensus_timestamp)
                    select abc.account_id, e.balance, :consensusTimestamp
                    from account_balance_change abc
                    join entity e on e.id = abc.account_id
                    where e.balance is not null
                      and e.balance_timestamp > :minConsensusTimestamp
                      and e.id <> :treasuryAccountId

                    union all

                    select e.id, e.balance, :consensusTimestamp
                    from entity e
                    where e.id = :treasuryAccountId
                    """)
    @Transactional(propagation = Propagation.MANDATORY)
    int balanceSnapshotDeduplicate(long minConsensusTimestamp, long consensusTimestamp, long treasuryAccountId);

    @Query(
            nativeQuery = true,
            value =
                    """
                    select max(consensus_timestamp) as consensus_timestamp
                    from account_balance
                    where account_id = :treasuryAccountId and consensus_timestamp >= :lowerRangeTimestamp
                            and consensus_timestamp < :upperRangeTimestamp
                    """)
    Optional<Long> getMaxConsensusTimestampInRange(
            long lowerRangeTimestamp, long upperRangeTimestamp, long treasuryAccountId);

    @Modifying
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(nativeQuery = true, value = "delete from account_balance_change")
    int deleteSnapshotRows();

    @Override
    @EntityGraph("AccountBalance.tokenBalances")
    List<AccountBalance> findAll();

    @EntityGraph("AccountBalance.tokenBalances")
    List<AccountBalance> findByIdConsensusTimestamp(long consensusTimestamp);
}
