// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.Optional;
import org.hiero.mirror.common.domain.file.FileData;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileDataRepository extends CrudRepository<FileData, Long> {

    @Query(nativeQuery = true, value = """
            select
              max(consensus_timestamp) as consensus_timestamp,
              ?1 as entity_id,
              string_agg(file_data, '' order by consensus_timestamp) as file_data,
              null as transaction_type
            from file_data
            where entity_id = ?1
              and consensus_timestamp >= (
                select consensus_timestamp
                from file_data
                where entity_id = ?1
                  and consensus_timestamp >= ?2
                  and consensus_timestamp <= ?3
                  and (transaction_type = 17 or (transaction_type = 19 and length(file_data) <> 0))
              order by consensus_timestamp desc
              limit 1
            ) and consensus_timestamp <= ?3
              and (transaction_type <> 19 or length(file_data) <> 0)
            """)
    Optional<FileData> getFileAtTimestamp(long fileId, long lowerTimestamp, long upperTimestamp);
}
