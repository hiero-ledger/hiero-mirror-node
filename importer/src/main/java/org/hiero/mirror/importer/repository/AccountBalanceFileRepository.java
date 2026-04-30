// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import java.util.Optional;
import org.hiero.mirror.common.domain.balance.AccountBalanceFile;
import org.springframework.data.jpa.repository.Query;

public interface AccountBalanceFileRepository extends StreamFileRepository<AccountBalanceFile, Long> {

    @Override
    @Query(value = "select * from account_balance_file order by consensus_timestamp desc limit 1", nativeQuery = true)
    Optional<AccountBalanceFile> findLatest();

    @Query(
            nativeQuery = true,
            value =
                    "select * from account_balance_file where consensus_timestamp < ?1 order by consensus_timestamp desc limit 1")
    Optional<AccountBalanceFile> findLatestBefore(long timestamp);

    @Query(
            nativeQuery = true,
            value = "select * from account_balance_file where consensus_timestamp >= ?1 "
                    + "and consensus_timestamp <= ?2 and consensus_timestamp <= (select max(consensus_end) from record_file) "
                    + "order by consensus_timestamp asc limit 1")
    Optional<AccountBalanceFile> findNextInRange(long startTimestamp, long endTimestamp);
}
