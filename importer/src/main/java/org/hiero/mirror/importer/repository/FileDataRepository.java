// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.hiero.mirror.common.domain.file.FileData;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface FileDataRepository extends CrudRepository<FileData, Long> {
    @Query(
            value =
                    """
            select *
            from file_data
            where consensus_timestamp between ?1 and ?2 and entity_id = ?3 and transaction_type = ?4
            order by consensus_timestamp
            """,
            nativeQuery = true)
    List<FileData> findFilesInRange(long start, long end, long encodedEntityId, int transactionType);

    @Query(
            value =
                    """
            select *
            from file_data
            where consensus_timestamp < ?1 and entity_id = ?2 and transaction_type in (?3)
            order by consensus_timestamp desc
            limit 1
            """,
            nativeQuery = true)
    Optional<FileData> findLatestMatchingFile(
            long consensusTimestamp, long encodedEntityId, List<Integer> transactionTypes);

    @Query(
            value =
                    """
            select *
            from file_data
            where consensus_timestamp > ?1 and consensus_timestamp < ?2 and entity_id in (?3)
            order by consensus_timestamp
            limit ?4
            """,
            nativeQuery = true)
    List<FileData> findAddressBooksBetween(
            long startConsensusTimestamp, long endConsensusTimestamp, Collection<Long> entityIds, long limit);

    @Query(
            value =
                    """
      with  first as (
        select consensus_timestamp, transaction_type
        from file_data
        where entity_id = ?1
          and consensus_timestamp <= ?2
          and (transaction_type = 17
                 or (transaction_type = 19 and length(file_data) <> 0))
        order by consensus_timestamp desc
        limit 1
      )
      select
        (select consensus_timestamp from first) as consensus_timestamp,
        ?1 as entity_id,
        string_agg(file_data, '' order by consensus_timestamp) as file_data,
        (select transaction_type from first) as transaction_type
      from file_data
      where entity_id = ?1
        and consensus_timestamp >= (select consensus_timestamp from first)
        and consensus_timestamp <= ?2
      """,
            nativeQuery = true)
    Optional<FileData> getFileAtTimestamp(long fileId, long timestamp);
}
