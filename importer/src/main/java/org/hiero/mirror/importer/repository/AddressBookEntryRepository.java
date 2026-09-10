// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import java.util.Optional;
import org.hiero.mirror.common.domain.addressbook.AddressBookEntry;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface AddressBookEntryRepository extends CrudRepository<AddressBookEntry, AddressBookEntry.Id> {

    // The composite primary key is mapped flat with only consensus_timestamp as the Spring Data id, since the service
    // endpoints mapped collection doesn't support a composite embedded id, so query both key columns explicitly
    @Override
    @Query("select * from address_book_entry where consensus_timestamp = :#{#id.consensusTimestamp}"
            + " and node_id = :#{#id.nodeId}")
    Optional<AddressBookEntry> findById(AddressBookEntry.Id id);
}
