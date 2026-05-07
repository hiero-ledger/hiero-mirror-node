// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.repository;

import java.util.List;
import org.hiero.mirror.common.domain.addressbook.AddressBookServiceEndpoint;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface AddressBookServiceEndpointRepository
        extends CrudRepository<AddressBookServiceEndpoint, AddressBookServiceEndpoint.Id> {

    @Query("""
            select consensus_timestamp, ip_address_v4, node_id, port, domain_name
            from address_book_service_endpoint
            where consensus_timestamp = :consensusTimestamp and node_id = :nodeId
            """)
    List<AddressBookServiceEndpoint> findAllByConsensusTimestampAndNodeId(long consensusTimestamp, long nodeId);
}
