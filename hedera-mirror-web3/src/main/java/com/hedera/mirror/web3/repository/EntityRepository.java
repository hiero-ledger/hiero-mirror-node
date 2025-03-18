// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.repository;

import static com.hedera.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_ENTITY;
import static com.hedera.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;
import static com.hedera.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_ALIAS;
import static com.hedera.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_EVM_ADDRESS;

import com.hedera.mirror.common.domain.entity.Entity;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface EntityRepository extends CrudRepository<Entity, Long> {

    @Cacheable(cacheNames = CACHE_NAME, cacheManager = CACHE_MANAGER_ENTITY, unless = "#result == null")
    Optional<Entity> findByIdAndDeletedIsFalse(Long entityId);

    @Cacheable(
            cacheNames = CACHE_NAME_EVM_ADDRESS,
            cacheManager = CACHE_MANAGER_ENTITY,
            key = "T(java.util.Arrays).hashCode(#alias)",
            unless = "#result == null")
    Optional<Entity> findByEvmAddressAndDeletedIsFalseAndShardAndRealm(byte[] alias, long shard, long realm);

    @Cacheable(
            cacheNames = CACHE_NAME_ALIAS,
            cacheManager = CACHE_MANAGER_ENTITY,
            key = "T(java.util.Arrays).hashCode(#alias)",
            unless = "#result == null")
    @Query(
            value =
                    """
            select *
            from entity
            where (evm_address = ?1 or alias = ?1) and deleted is not true and shard = ?2 and realm = ?3
            """,
            nativeQuery = true)
    Optional<Entity> findByEvmAddressOrAliasAndShardAndRealm(byte[] alias, long shard, long realm);

    /**
     * Retrieves the most recent state of an entity by its evm address up to a given block timestamp.
     *
     * @param evmAddress      the evm address of the entity to be retrieved.
     * @param blockTimestamp  the block timestamp used to filter the results.
     * @param shard           the shard identifier that represents a partition of the network.
     * @param realm           the realm identifier that represents a logical grouping within the network.
     * @return an Optional containing the entity's state at the specified timestamp.
     *         If there is no record found for the given criteria, an empty Optional is returned.
     */
    @Query(
            value =
                    """
            with entity_cte as (
                select id
                from entity
                where evm_address = ?1 and created_timestamp <= ?2 and shard = ?3 and realm = ?4
                order by created_timestamp desc
                limit 1
            )
            (
                select *
                from entity e
                where e.deleted is not true
                and e.id = (select id from entity_cte)
            )
            union all
            (
                select *
                from entity_history eh
                where lower(eh.timestamp_range) <= ?2
                and eh.id = (select id from entity_cte)
                order by lower(eh.timestamp_range) desc
                limit 1
            )
            order by timestamp_range desc
            limit 1
            """,
            nativeQuery = true)
    Optional<Entity> findActiveByEvmAddressAndTimestampAndShardAndRealm(byte[] evmAddress, long blockTimestamp, long shard, long realm);

    /**
     * Retrieves the most recent state of an entity by its alias up to a given block timestamp.
     *
     * @param alias           the alias of the entity to be retrieved.
     * @param blockTimestamp  the block timestamp used to filter the results.
     * @param shard           the shard identifier that represents a partition of the network.
     * @param realm           the realm identifier that represents a logical grouping within the network.
     * @return an Optional containing the entity's state at the specified timestamp.
     *         If there is no record found for the given criteria, an empty Optional is returned.
     */
    @Query(
            value =
                    """
            with entity_cte as (
                select id
                from entity
                where (evm_address = ?1 or alias = ?1) and created_timestamp <= ?2 and shard = ?3 and realm = ?4
                order by created_timestamp desc
                limit 1
            )
            (
                select *
                from entity e
                where e.deleted is not true
                and e.id = (select id from entity_cte)
            )
            union all
            (
                select *
                from entity_history eh
                where lower(eh.timestamp_range) <= ?2
                and eh.id = (select id from entity_cte)
                order by lower(eh.timestamp_range) desc
                limit 1
            )
            order by timestamp_range desc
            limit 1
            """,
            nativeQuery = true)
    Optional<Entity> findActiveByEvmAddressOrAliasAndTimestampAndShardAndRealm(byte[] alias, long blockTimestamp, long shard, long realm);

    /**
     * Retrieves the most recent state of an entity by its ID up to a given block timestamp.
     * The method considers both the current state of the entity and its historical states
     * and returns the one that was valid just before or equal to the provided block timestamp.
     * It performs a UNION operation between the 'entity' and 'entity_history' tables,
     * filters the combined result set to get the records with a timestamp range
     * less than or equal to the provided block timestamp and then returns the most recent record.
     *
     * @param id              the ID of the entity to be retrieved.
     * @param blockTimestamp  the block timestamp used to filter the results.
     * @return an Optional containing the entity's state at the specified timestamp.
     *         If there is no record found for the given criteria, an empty Optional is returned.
     */
    @Query(
            value =
                    """
                    (
                        select *
                        from entity
                        where id = ?1 and lower(timestamp_range) <= ?2
                        and deleted is not true
                    )
                    union all
                    (
                        select *
                        from entity_history
                        where id = ?1 and lower(timestamp_range) <= ?2
                        and deleted is not true
                        order by lower(timestamp_range) desc
                        limit 1
                    )
                    order by timestamp_range desc
                    limit 1
                    """,
            nativeQuery = true)
    Optional<Entity> findActiveByIdAndTimestamp(long id, long blockTimestamp);

    @Query(
            value =
                    """
                    select id
                    from entity
                    where shard = ?1 and realm = ?2
                    order by id desc
                    limit 1
                    """,
            nativeQuery = true)
    Long findMaxId(long shard, long realm);
}
