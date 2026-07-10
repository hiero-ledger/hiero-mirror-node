// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import java.util.Optional;
import org.hiero.mirror.common.domain.addressbook.AddressBook;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface AddressBookRepository extends CrudRepository<AddressBook, Long> {
    @Query(
            value =
                    "select * from address_book where start_consensus_timestamp <= :consensusTimestamp and file_id = :encodedFileId order by "
                            + "start_consensus_timestamp desc limit 1")
    Optional<AddressBook> findLatest(long consensusTimestamp, long encodedFileId);

    // AddressBook.isNew() is always true so save() only inserts; existing rows are updated through this query
    @Modifying
    @Query("update address_book set end_consensus_timestamp = :endConsensusTimestamp"
            + " where start_consensus_timestamp = :startConsensusTimestamp")
    int updateEndConsensusTimestamp(long startConsensusTimestamp, long endConsensusTimestamp);
}
