// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.repository;

import java.util.Optional;
import org.hiero.mirror.common.domain.addressbook.AddressBook;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface AddressBookRepository extends CrudRepository<AddressBook, Long> {

    @Query(value = "select max(start_consensus_timestamp) from address_book where file_id = ?", nativeQuery = true)
    Optional<Long> findLatestTimestamp(long fileId);
}
