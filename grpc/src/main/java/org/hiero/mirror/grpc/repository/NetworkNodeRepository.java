// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.repository;

import java.util.List;
import org.hiero.mirror.common.domain.addressbook.AddressBookEntry;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

public interface NetworkNodeRepository extends Repository<AddressBookEntry, AddressBookEntry.Id> {

    record NetworkNodeView(
            long consensusTimestamp,
            String description,
            String memo,
            long nodeId,
            byte[] nodeCertHash,
            String publicKey,
            Long stake,
            Long nodeAccountId,
            String serviceEndpointsJson) {}

    @Query(value = """
                    select
                      abe.consensus_timestamp as consensus_timestamp,
                      abe.description as description,
                      abe.memo as memo,
                      abe.node_id as node_id,
                      abe.node_cert_hash as node_cert_hash,
                      abe.public_key as public_key,
                      abe.stake as stake,
                      coalesce(n.account_id, abe.node_account_id) as node_account_id,
                      coalesce((
                        select jsonb_agg(
                          jsonb_build_object(
                            'domain_name', coalesce(abse.domain_name, ''),
                            'ip_address_v4', coalesce(abse.ip_address_v4, ''),
                            'port', abse.port
                          ) order by abse.ip_address_v4 asc, abse.port asc
                        )
                        from address_book_service_endpoint abse
                        where abse.consensus_timestamp = abe.consensus_timestamp
                          and abse.node_id = abe.node_id
                      ), '[]'::jsonb)::text as service_endpoints_json
                    from address_book_entry abe
                    left join node n on n.node_id = abe.node_id
                    where abe.consensus_timestamp = :consensusTimestamp
                      and abe.node_id >= :minNodeId
                    order by abe.node_id asc
                    limit :limit
                    """)
    List<NetworkNodeView> findByConsensusTimestampAndMinNodeId(long consensusTimestamp, long minNodeId, int limit);
}
