// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.Optional;
import org.hiero.mirror.common.domain.file.FileData;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FileDataRepository extends CrudRepository<FileData, Long> {

    @Query(value = """
            select
              max(consensus_timestamp) as consensus_timestamp,
              :fileId as entity_id,
              string_agg(file_data, '' order by consensus_timestamp) as file_data,
              null as transaction_type
            from file_data
            where entity_id = :fileId
              and consensus_timestamp >= (
                select consensus_timestamp
                from file_data
                where entity_id = :fileId
                  and consensus_timestamp >= :lowerTimestamp
                  and consensus_timestamp <= :upperTimestamp
                  and (transaction_type = 17 or (transaction_type = 19 and length(file_data) <> 0))
              order by consensus_timestamp desc
              limit 1
            ) and consensus_timestamp <= :upperTimestamp
              and (transaction_type <> 19 or length(file_data) <> 0)
            """)
    Optional<FileData> getFileAtTimestamp(
            @Param("fileId") long fileId,
            @Param("lowerTimestamp") long lowerTimestamp,
            @Param("upperTimestamp") long upperTimestamp);

    @Query(value = """
            select max(consensus_timestamp)
            from file_data
            where entity_id = :fileId
              and (transaction_type = 17 or (transaction_type = 19 and length(file_data) <> 0))
            """)
    Optional<Long> getLatestTimestamp(@Param("fileId") long fileId);
}
