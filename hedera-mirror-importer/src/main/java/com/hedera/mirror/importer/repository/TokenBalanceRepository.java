// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

import com.hedera.mirror.common.domain.balance.TokenBalance;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

public interface TokenBalanceRepository
        extends BalanceSnapshotRepository, CrudRepository<TokenBalance, TokenBalance.Id> {

    @Modifying
    @Override
    @Query(
            nativeQuery = true,
            value =
                    """
        insert into token_balance (account_id, balance, consensus_timestamp, token_id)
        select account_id, balance, :consensusTimestamp, token_id
        from token_account
        where associated is true or balance_timestamp > (
            select coalesce(max(consensus_timestamp), 0)
            from account_balance
            where account_id = 2 and
              consensus_timestamp > :consensusTimestamp - 2592000000000000 and
              consensus_timestamp < :consensusTimestamp
        )
        order by account_id, token_id
        """)
    @Transactional
    int balanceSnapshot(long consensusTimestamp);

    @Override
    @Modifying
    @Query(
            nativeQuery = true,
            value =
                    """
        insert into token_balance (account_id, balance, consensus_timestamp, token_id)
        select account_id, balance, :consensusTimestamp, token_id
        from token_account
        where balance_timestamp > :maxConsensusTimestamp
        order by account_id, token_id
        """)
    @Transactional
    int balanceSnapshotDeduplicate(long maxConsensusTimestamp, long consensusTimestamp);
}
