// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import java.util.Optional;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface RecordFileRepository extends PagingAndSortingRepository<RecordFile, Long> {

    @Query(value = "select * from record_file where hash like concat(cast(:hash as text), '%')", nativeQuery = true)
    Optional<RecordFile> findByHash(String hash);

    @Query(value = "select * from record_file where index = :index", nativeQuery = true)
    Optional<RecordFile> findByIndex(long index);

    @Query(
            value = "select * from record_file where consensus_end >= :timestamp order by consensus_end asc limit 1",
            nativeQuery = true)
    Optional<RecordFile> findByTimestamp(long timestamp);

    @Query(value = "select * from record_file order by index asc limit 1", nativeQuery = true)
    Optional<RecordFile> findEarliest();

    @Query(value = "select * from record_file order by consensus_end desc limit 1", nativeQuery = true)
    Optional<RecordFile> findLatest();
}
