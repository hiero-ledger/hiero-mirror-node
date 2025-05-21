// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import java.util.Optional;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface RecordFileRepository extends StreamFileRepository<RecordFile, Long>, RetentionRepository {

    @Query(value = "select r from RecordFile r order by r.consensusEnd limit 1")
    Optional<RecordFile> findFirst();

    @Override
    @Query(value = "select * from record_file order by consensus_end desc limit 1", nativeQuery = true)
    Optional<RecordFile> findLatest();

    @Query(
            nativeQuery = true,
            value = "select * from record_file where consensus_end < "
                    + "(select max(consensus_end) from record_file) - ?1 order by consensus_end desc limit 1")
    Optional<RecordFile> findLatestWithOffset(long offset);

    @Query(
            nativeQuery = true,
            value = "select * from record_file where consensus_end < ?1 order by consensus_end desc limit 1")
    Optional<RecordFile> findLatestBefore(long offset);

    @Query(
            value = "select * from record_file where consensus_end < ?1 and gas_used = -1 order by consensus_end desc "
                    + "limit 1",
            nativeQuery = true)
    Optional<RecordFile> findLatestMissingGasUsedBefore(long consensusTimestamp);

    @Query(
            value = "select * from record_file where consensus_end > ?1 and consensus_end <= ?2 "
                    + "order by consensus_end asc limit 1",
            nativeQuery = true)
    Optional<RecordFile> findNextBetween(long minTimestampExclusive, long maxTimestampInclusive);

    @Modifying
    @Override
    @Query("delete from RecordFile where consensusEnd <= ?1")
    int prune(long consensusTimestamp);

    @Modifying
    @Query(nativeQuery = true, value = "update record_file set index = index + ?1")
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 7200)
    int updateIndex(long offset);
}
