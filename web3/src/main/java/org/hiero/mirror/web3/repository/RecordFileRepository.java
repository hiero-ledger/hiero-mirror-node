// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import java.util.Optional;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface RecordFileRepository extends PagingAndSortingRepository<RecordFile, Long> {

    @Query("select * from record_file where hash like concat(cast(:hash as text), '%')")
    Optional<RecordFile> findByHash(String hash);

    @Query("select * from record_file where index = :index")
    Optional<RecordFile> findByIndex(long index);

    @Query("select * from record_file where consensus_end >= :timestamp order by consensus_end asc limit 1")
    Optional<RecordFile> findByTimestamp(long timestamp);

    @Query("select * from record_file order by index asc limit 1")
    Optional<RecordFile> findEarliest();

    @Query("select * from record_file order by consensus_end desc limit 1")
    Optional<RecordFile> findLatest();
}
