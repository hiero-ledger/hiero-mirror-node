// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_ENTITY;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_SYSTEM_ACCOUNT;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_ALIAS;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_EVM_ADDRESS;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.hiero.mirror.common.domain.entity.Entity;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface EntityRepository extends CrudRepository<Entity, Long> {

    @Caching(
            cacheable = {
                @Cacheable(
                        cacheNames = CACHE_NAME,
                        cacheManager = CACHE_MANAGER_ENTITY,
                        unless = "@spelHelper.isNullOrEmpty(#result)"),
                @Cacheable(
                        cacheNames = CACHE_NAME,
                        cacheManager = CACHE_MANAGER_SYSTEM_ACCOUNT,
                        condition =
                                "#entityId < 1000 && !T(org.hiero.mirror.web3.common.ContractCallContext).isBalanceCallSafe()",
                        unless = "@spelHelper.isNullOrEmpty(#result)")
            })
    Optional<Entity> findByIdAndDeletedIsFalse(Long entityId);

    @Cacheable(
            cacheNames = CACHE_NAME_EVM_ADDRESS,
            cacheManager = CACHE_MANAGER_ENTITY,
            key = "@spelHelper.getCacheKey(#alias)",
            unless = "@spelHelper.isNullOrEmpty(#result)")
    @Query(value = "select * from entity where evm_address = :alias and deleted is not true")
    Optional<Entity> findByEvmAddressAndDeletedIsFalse(byte[] alias);

    @Cacheable(
            cacheNames = CACHE_NAME_ALIAS,
            cacheManager = CACHE_MANAGER_ENTITY,
            key = "@spelHelper.getCacheKey(#alias)",
            unless = "@spelHelper.isNullOrEmpty(#result)")
    @Query("""
            select *
            from entity
            where (evm_address = :alias or alias = :alias) and deleted is not true
            """)
    Optional<Entity> findByEvmAddressOrAliasAndDeletedIsFalse(byte[] alias);

    /**
     * Retrieves the state of an entity by its evm address at a given block timestamp, selecting the row whose
     * timestamp_range contains the block timestamp and returning it only if the entity was not deleted at that point in
     * time. If the entity had already been deleted at or before the block timestamp, an empty Optional is returned.
     *
     * @param evmAddress      the evm address of the entity to be retrieved.
     * @param blockTimestamp  the block timestamp used to filter the results.
     * @return an Optional containing the entity's state at the specified timestamp.
     *         If there is no record found for the given criteria, an empty Optional is returned.
     */
    @Query("""
            with entity_cte as (
                select id
                from entity
                where evm_address = :evmAddress and created_timestamp <= :blockTimestamp
                order by created_timestamp desc
                limit 1
            )
            (
                select *
                from entity e
                where e.deleted is not true
                and e.id = (select id from entity_cte)
                and lower(e.timestamp_range) <= :blockTimestamp
            )
            union all
            (
                select * from (
                    select *
                    from entity_history eh
                    where eh.id = (select id from entity_cte)
                    and lower(eh.timestamp_range) <= :blockTimestamp
                    order by lower(eh.timestamp_range) desc
                    limit 1
                ) latest_history
                where deleted is not true and timestamp_range @> :blockTimestamp
            )
            order by timestamp_range desc
            limit 1
            """)
    Optional<Entity> findActiveByEvmAddressAndTimestamp(byte[] evmAddress, long blockTimestamp);

    /**
     * Retrieves the state of an entity by its alias at a given block timestamp, selecting the row whose timestamp_range
     * contains the block timestamp and returning it only if the entity was not deleted at that point in time. If the
     * entity had already been deleted at or before the block timestamp, an empty Optional is returned.
     *
     * @param alias           the alias of the entity to be retrieved.
     * @param blockTimestamp  the block timestamp used to filter the results.
     * @return an Optional containing the entity's state at the specified timestamp.
     *         If there is no record found for the given criteria, an empty Optional is returned.
     */
    @Query("""
            with entity_cte as (
                select id
                from entity
                where created_timestamp <= :blockTimestamp and (evm_address = :alias or alias = :alias)
                order by created_timestamp desc
                limit 1
            )
            (
                select *
                from entity e
                where e.deleted is not true
                and e.id = (select id from entity_cte)
                and lower(e.timestamp_range) <= :blockTimestamp
            )
            union all
            (
                select * from (
                    select *
                    from entity_history eh
                    where eh.id = (select id from entity_cte)
                    and lower(eh.timestamp_range) <= :blockTimestamp
                    order by lower(eh.timestamp_range) desc
                    limit 1
                ) latest_history
                where deleted is not true and timestamp_range @> :blockTimestamp
            )
            order by timestamp_range desc
            limit 1
            """)
    Optional<Entity> findActiveByEvmAddressOrAliasAndTimestamp(byte[] alias, long blockTimestamp);

    /**
     * Retrieves the most recent state of each entity matching any of the given evm addresses or aliases up to a given
     * block timestamp. Address resolution uses the current {@code entity} row (the same source as
     * {@link #findActiveByEvmAddressOrAliasAndTimestamp(byte[], long)}), then the newest non-deleted version active at
     * or before the block timestamp is taken from {@code entity} or {@code entity_history}.
     *
     * @param addresses      the evm addresses or aliases to look up.
     * @param blockTimestamp the block timestamp used to filter the results.
     * @return the list of active entities (at most one per resolved entity ID); addresses with no active version are
     *     omitted.
     */
    @Query("""
            with entity_cte as (
                select id
                from entity
                where created_timestamp <= :blockTimestamp and (evm_address in (:addresses) or alias in (:addresses))
            )
            select distinct on (id) *
            from (
                (
                    select *
                    from entity
                    where id in (select id from entity_cte)
                    and deleted is not true
                    and timestamp_range @> :blockTimestamp
                )
                union all
                (
                    select *
                    from entity_history
                    where id in (select id from entity_cte)
                    and deleted is not true
                    and timestamp_range @> :blockTimestamp
                )
            ) as merged
            order by id, lower(timestamp_range) desc
            """)
    List<Entity> findActiveByEvmAddressesOrAliasesAndTimestamp(Collection<byte[]> addresses, long blockTimestamp);

    /**
     * Retrieves the state of an entity by its ID at a given block timestamp.
     * The method considers both the current state of the entity and its historical states,
     * selecting the row whose timestamp_range contains the block timestamp, and returns it only
     * if the entity was not deleted at that point in time. If the entity had already been deleted
     * at or before the block timestamp, an empty Optional is returned.
     *
     * @param id              the ID of the entity to be retrieved.
     * @param blockTimestamp  the block timestamp used to filter the results.
     * @return an Optional containing the entity's state at the specified timestamp.
     *         If there is no record found for the given criteria, an empty Optional is returned.
     */
    @Query("""
                    (
                        select *
                        from entity
                        where id = :id and lower(timestamp_range) <= :blockTimestamp
                        and deleted is not true
                    )
                    union all
                    (
                        select * from (
                            select *
                            from entity_history
                            where id = :id and lower(timestamp_range) <= :blockTimestamp
                            order by lower(timestamp_range) desc
                            limit 1
                        ) latest_history
                        where deleted is not true and timestamp_range @> :blockTimestamp
                    )
                    order by timestamp_range desc
                    limit 1
                    """)
    Optional<Entity> findActiveByIdAndTimestamp(long id, long blockTimestamp);

    /**
     * Retrieves the most recent state of each requested entity by ID up to a given block timestamp.
     * Returns at most one row per ID (the newest non-deleted version whose {@code timestamp_range} contains the
     * block timestamp), consolidating the current {@code entity} row and any matching {@code entity_history} rows.
     *
     * @param ids            the entity IDs to look up.
     * @param blockTimestamp the block timestamp used to filter results.
     * @return the list of active entities (at most one per requested ID); IDs with no active version are omitted.
     */
    @Query("""
                    select distinct on (id) *
                    from (
                        (
                            select *
                            from entity
                            where id in (:ids)
                              and timestamp_range @> :blockTimestamp
                              and deleted is not true
                        )
                        union all
                        (
                            select *
                            from entity_history
                            where id in (:ids)
                              and timestamp_range @> :blockTimestamp
                              and deleted is not true
                        )
                    ) as merged
                    order by id, lower(timestamp_range) desc
                    """)
    List<Entity> findActiveByIdsAndTimestamp(Collection<Long> ids, long blockTimestamp);

    @Query("""
                    select id
                    from entity
                    order by id desc
                    limit 1
                    """)
    Long findMaxId();
}
