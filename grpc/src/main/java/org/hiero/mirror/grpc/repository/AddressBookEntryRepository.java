// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.repository;

import static org.hiero.mirror.grpc.config.CacheConfiguration.ADDRESS_BOOK_ENTRY_CACHE;
import static org.hiero.mirror.grpc.config.CacheConfiguration.CACHE_NAME;

import java.util.List;
import org.hiero.mirror.common.domain.addressbook.AddressBookEntry;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface AddressBookEntryRepository extends CrudRepository<AddressBookEntry, AddressBookEntry.Id> {

    @Cacheable(
            cacheManager = ADDRESS_BOOK_ENTRY_CACHE,
            cacheNames = CACHE_NAME,
            unless = "#result == null or #result.size() == 0")
    @Query(value = """
        select abe.consensus_timestamp,
               abe.description,
               abe.memo,
               abe.node_id,
               abe.node_cert_hash,
               abe.public_key,
               abe.stake,
               coalesce(n.account_id, abe.node_account_id) as node_account_id
        from address_book_entry abe
        left join node n on n.node_id = abe.node_id
        where abe.consensus_timestamp = ?
          and abe.node_id >= ?
        order by abe.node_id asc
        limit ?
        """, nativeQuery = true)
    List<AddressBookEntry> findByConsensusTimestampAndNodeId(long consensusTimestamp, long nodeId, int limit);
}
