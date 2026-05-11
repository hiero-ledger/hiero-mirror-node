// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.repository;

import static org.hiero.mirror.grpc.config.CacheConfiguration.ADDRESS_BOOK_ENTRY_CACHE;
import static org.hiero.mirror.grpc.config.CacheConfiguration.CACHE_NAME;

import java.util.List;
import org.hiero.mirror.common.domain.addressbook.AddressBookEntry;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface AddressBookEntryRepository extends CrudRepository<AddressBookEntry, AddressBookEntry.Id> {

    record AddressBookEntryView(
            long consensusTimestamp,
            String description,
            String memo,
            long nodeId,
            byte[] nodeCertHash,
            String publicKey,
            Long stake,
            Long nodeAccountId) {}

    @Cacheable(
            cacheManager = ADDRESS_BOOK_ENTRY_CACHE,
            cacheNames = CACHE_NAME,
            unless = "@spelHelper.isNullOrEmpty(#result)")
    @Query(value = """
        select abe.consensus_timestamp as consensus_timestamp,
               abe.description as description,
               abe.memo as memo,
               abe.node_id as node_id,
               abe.node_cert_hash as node_cert_hash,
               abe.public_key as public_key,
               abe.stake as stake,
               coalesce(n.account_id, abe.node_account_id) as node_account_id
        from address_book_entry abe
        left join node n on n.node_id = abe.node_id
        where abe.consensus_timestamp = :consensusTimestamp
          and abe.node_id >= :nodeId
        order by abe.node_id asc
        limit :limit
        """)
    List<AddressBookEntryView> findByConsensusTimestampAndNodeId(long consensusTimestamp, long nodeId, int limit);
}
