// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.hiero.mirror.common.domain.file.FileData;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface FileDataRepository extends CrudRepository<FileData, Long> {
    @Query(value = """
            select *
            from file_data
            where consensus_timestamp between :start and :end and entity_id = :encodedEntityId and transaction_type = :transactionType
            order by consensus_timestamp
            """)
    List<FileData> findFilesInRange(long start, long end, long encodedEntityId, int transactionType);

    @Query(value = """
            select *
            from file_data
            where consensus_timestamp < :consensusTimestamp and entity_id = :encodedEntityId and transaction_type in (:transactionTypes)
            order by consensus_timestamp desc
            limit 1
            """)
    Optional<FileData> findLatestMatchingFile(
            long consensusTimestamp, long encodedEntityId, List<Integer> transactionTypes);

    @Query(value = """
            select *
            from file_data
            where consensus_timestamp > :startConsensusTimestamp and consensus_timestamp < :endConsensusTimestamp and entity_id in (:entityIds)
            order by consensus_timestamp
            limit :limit
            """)
    List<FileData> findAddressBooksBetween(
            long startConsensusTimestamp, long endConsensusTimestamp, Collection<Long> entityIds, long limit);

    @Query("""
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
                  and consensus_timestamp <= :timestamp
                  and (transaction_type = 17 or (transaction_type = 19 and length(file_data) <> 0))
              order by consensus_timestamp desc
              limit 1
            ) and consensus_timestamp <= :timestamp
              and (transaction_type <> 19 or length(file_data) <> 0)
            """)
    Optional<FileData> getFileAtTimestamp(long fileId, long timestamp);
}
