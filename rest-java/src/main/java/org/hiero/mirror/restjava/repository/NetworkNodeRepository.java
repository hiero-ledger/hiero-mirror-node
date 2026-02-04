// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.List;
import org.hiero.mirror.common.domain.addressbook.AddressBookEntry;
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
     * @return List of network node query result rows
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
                abe.description as description,
                abe.memo as memo,
                abe.node_id as nodeId,
                coalesce(n.account_id, abe.node_account_id) as nodeAccountId,
                abe.node_cert_hash as nodeCertHash,
                abe.public_key as publicKey,
                ab.file_id as fileId,
                ab.start_consensus_timestamp as startConsensusTimestamp,
                ab.end_consensus_timestamp as endConsensusTimestamp,
                n.admin_key as adminKey,
                n.decline_reward as declineReward,
                n.grpc_proxy_endpoint as grpcProxyEndpoint,
                ns.max_stake as maxStake,
                ns.min_stake as minStake,
                ns.reward_rate as rewardRateStart,
                ns.stake as stake,
                ns.stake_not_rewarded as stakeNotRewarded,
                ns.stake_rewarded as stakeRewarded,
                ns.staking_period as stakingPeriod,
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
                ), '[]'::jsonb) as serviceEndpoints
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
              case when :orderDirection = 'ASC' then abe.node_id end asc,
              case when :orderDirection = 'DESC' then abe.node_id end desc
            limit :limit
            """, nativeQuery = true)
    List<NetworkNodeRow> findNetworkNodes(
            Long fileId, Long[] nodeIds, long minNodeId, long maxNodeId, String orderDirection, int limit);
}
