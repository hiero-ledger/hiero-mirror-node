// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.repository;

import static org.hiero.mirror.grpc.config.CacheConfiguration.ADDRESS_BOOK_ENTRY_CACHE;
import static org.hiero.mirror.grpc.config.CacheConfiguration.CACHE_NAME;

import com.hedera.mirror.common.domain.addressbook.AddressBookEntry;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface AddressBookEntryRepository extends CrudRepository<AddressBookEntry, AddressBookEntry.Id> {

    @Cacheable(
            cacheManager = ADDRESS_BOOK_ENTRY_CACHE,
            cacheNames = CACHE_NAME,
            unless = "#result == null or #result.size() == 0")
    @Query(
            value = "select * from address_book_entry where consensus_timestamp = ? and node_id >= ? "
                    + "order by node_id asc limit ?",
            nativeQuery = true)
    List<AddressBookEntry> findByConsensusTimestampAndNodeId(long consensusTimestamp, long nodeId, int limit);
}
