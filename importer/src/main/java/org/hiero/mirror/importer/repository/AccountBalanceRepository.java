// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import java.util.List;
import java.util.Optional;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

public interface AccountBalanceRepository
        extends BalanceSnapshotRepository, CrudRepository<AccountBalance, AccountBalance.Id> {

    @Modifying
    @Override
    @Query("""
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
    @Query("""
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

    @Query("""
          select max(consensus_timestamp) as consensus_timestamp
          from account_balance
          where account_id = :treasuryAccountId and consensus_timestamp >= :lowerRangeTimestamp
                  and consensus_timestamp < :upperRangeTimestamp
        """)
    Optional<Long> getMaxConsensusTimestampInRange(
            long lowerRangeTimestamp, long upperRangeTimestamp, long treasuryAccountId);

    @Override
    List<AccountBalance> findAll();

    List<AccountBalance> findByIdConsensusTimestamp(long consensusTimestamp);
}
