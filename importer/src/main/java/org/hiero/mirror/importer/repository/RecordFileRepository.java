// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import java.util.Optional;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface RecordFileRepository extends StreamFileRepository<RecordFile, Long>, RetentionRepository {

    @Query("select * from record_file order by consensus_end limit 1")
    Optional<RecordFile> findFirst();

    @Override
    @Query("select * from record_file order by consensus_end desc limit 1")
    Optional<RecordFile> findLatest();

    @Query(
            value = "select * from record_file where consensus_end < "
                    + "(select max(consensus_end) from record_file) - :offset order by consensus_end desc limit 1")
    Optional<RecordFile> findLatestWithOffset(long offset);

    @Query("select * from record_file where consensus_end < :offset order by consensus_end desc limit 1")
    Optional<RecordFile> findLatestBefore(long offset);

    @Query(
            value =
                    "select * from record_file where consensus_end < :consensusTimestamp and gas_used = -1 order by consensus_end desc "
                            + "limit 1")
    Optional<RecordFile> findLatestMissingGasUsedBefore(long consensusTimestamp);

    @Query(
            value =
                    "select * from record_file where consensus_end > :minTimestampExclusive and consensus_end <= :maxTimestampInclusive "
                            + "order by consensus_end asc limit 1")
    Optional<RecordFile> findNextBetween(long minTimestampExclusive, long maxTimestampInclusive);

    @Modifying
    @Override
    @Query("delete from record_file where consensus_end <= :consensusTimestamp")
    int prune(long consensusTimestamp);

    @Modifying
    @Query("update record_file set index = index + :offset")
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 7200)
    int updateIndex(long offset);
}
