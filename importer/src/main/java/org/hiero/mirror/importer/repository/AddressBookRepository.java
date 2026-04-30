// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import java.util.Optional;
import org.hiero.mirror.common.domain.addressbook.AddressBook;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface AddressBookRepository extends CrudRepository<AddressBook, Long> {
    @Query(
            value = "select * from address_book where start_consensus_timestamp <= ?1 and file_id = ?2 order by "
                    + "start_consensus_timestamp desc limit 1",
            nativeQuery = true)
    Optional<AddressBook> findLatest(long consensusTimestamp, long encodedFileId);
}
