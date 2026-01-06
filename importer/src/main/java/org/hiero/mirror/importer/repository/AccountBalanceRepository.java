// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import java.util.List;
import java.util.Optional;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

public interface AccountBalanceRepository
        extends BalanceSnapshotRepository, CrudRepository<AccountBalance, AccountBalance.Id> {

    @Modifying
    @Override
    @Query(nativeQuery = true, value = """
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
    @Transactional
    int balanceSnapshot(long consensusTimestamp, long treasuryAccountId);

    @Override
    @Modifying
    @Query(nativeQuery = true, value = """
        insert into account_balance (account_id, balance, consensus_timestamp)
        select id, balance, :consensusTimestamp
        from entity
        where
          id = :treasuryAccountId or
          (balance is not null and
           balance_timestamp > :minConsensusTimestamp)
        order by id
        """)
    @Transactional
    int balanceSnapshotDeduplicate(long minConsensusTimestamp, long consensusTimestamp, long treasuryAccountId);

    @Query(nativeQuery = true, value = """
          select max(consensus_timestamp) as consensus_timestamp
          from account_balance
          where account_id = :treasuryAccountId and consensus_timestamp >= :lowerRangeTimestamp
                  and consensus_timestamp < :upperRangeTimestamp
        """)
    Optional<Long> getMaxConsensusTimestampInRange(
            long lowerRangeTimestamp, long upperRangeTimestamp, long treasuryAccountId);

    @Override
    @EntityGraph("AccountBalance.tokenBalances")
    List<AccountBalance> findAll();

    @EntityGraph("AccountBalance.tokenBalances")
    List<AccountBalance> findByIdConsensusTimestamp(long consensusTimestamp);
}
