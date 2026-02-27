// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.List;
import org.hiero.mirror.common.domain.addressbook.AddressBookEntry;
import org.hiero.mirror.restjava.dto.NetworkNodeDto;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface NetworkNodeRepository extends CrudRepository<AddressBookEntry, AddressBookEntry.Id> {

    /**
     * Unified query that handles optional nodeIds[] parameters. Performance is maintained through conditional SQL that
     * PostgreSQL optimizes efficiently.
     *
     * @param fileId         File ID for filtering address book (defaults to 102)
     * @param nodeIds        Optional array of node IDs for IN clause (use empty array to skip)
     * @param minNodeId      Minimum node ID for range filter (inclusive)
     * @param maxNodeId      Maximum node ID for range filter (inclusive)
     * @param orderDirection Sort direction ('ASC' or 'DESC')
     * @param limit          Maximum number of results to return
     * @return List of network node query results
     */
    @Query(value = """
            with latest_address_book as (
                select start_consensus_timestamp, end_consensus_timestamp, file_id
                from address_book
                where file_id = :fileId
                order by start_consensus_timestamp desc
                limit 1
            ),
            latest_node_stake as (
                select max_stake, min_stake, node_id, reward_rate,
                       stake, stake_not_rewarded, stake_rewarded,
                       staking_period
                from node_stake
                where consensus_timestamp = (select max(consensus_timestamp) from node_stake)
            ),
            node_info as (
                select admin_key, decline_reward, grpc_proxy_endpoint, node_id, account_id
                from node
            )
            select
                n.admin_key as adminKey,
                n.decline_reward as declineReward,
                abe.description as description,
                ab.end_consensus_timestamp as endConsensusTimestamp,
                ab.file_id as fileId,
                case when n.grpc_proxy_endpoint is null then null
                     else jsonb_build_object(
                         'domain_name', coalesce(n.grpc_proxy_endpoint->>'domain_name', ''),
                         'ip_address_v4', coalesce(n.grpc_proxy_endpoint->>'ip_address_v4', ''),
                         'port', (n.grpc_proxy_endpoint->>'port')::integer
                     )::text
                     end as grpcProxyEndpointJson,
                nullif(ns.max_stake, -1) as maxStake,
                abe.memo as memo,
                nullif(ns.min_stake, -1) as minStake,
                coalesce(n.account_id, abe.node_account_id) as nodeAccountId,
                case when abe.node_cert_hash is null or abe.node_cert_hash = ''::bytea then '0x'
                     when left(convert_from(abe.node_cert_hash, 'UTF8'), 2) = '0x' then convert_from(abe.node_cert_hash, 'UTF8')
                     else '0x' || convert_from(abe.node_cert_hash, 'UTF8')
                     end as nodeCertHash,
                abe.node_id as nodeId,
                case when abe.public_key is null or abe.public_key = '' then '0x'
                     when left(abe.public_key, 2) = '0x' then abe.public_key
                     else '0x' || abe.public_key
                     end as publicKey,
                ns.reward_rate as rewardRateStart,
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
                ), '[]'::jsonb)::text as serviceEndpointsJson,
                ns.stake as stake,
                nullif(ns.stake_not_rewarded, -1) as stakeNotRewarded,
                ns.stake_rewarded as stakeRewarded,
                ns.staking_period as stakingPeriod,
                ab.start_consensus_timestamp as startConsensusTimestamp
            from address_book_entry abe
            join latest_address_book ab
              on ab.start_consensus_timestamp = abe.consensus_timestamp
            left join latest_node_stake ns
              on abe.node_id = ns.node_id
            left join node_info n
              on abe.node_id = n.node_id
            where (coalesce(array_length(:nodeIds, 1), 0) = 0 or abe.node_id = any(:nodeIds))
              and abe.node_id >= :minNodeId
              and abe.node_id <= :maxNodeId
            order by
              case when CAST(:orderDirection AS text) = 'ASC' then abe.node_id end asc,
              case when CAST(:orderDirection AS text) = 'DESC' then abe.node_id end desc
            limit :limit
            """, nativeQuery = true)
    List<NetworkNodeDto> findNetworkNodes(
            Long fileId, Long[] nodeIds, long minNodeId, long maxNodeId, String orderDirection, int limit);
}
