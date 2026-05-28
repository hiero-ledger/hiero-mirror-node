// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_ENTITY;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_SYSTEM_ACCOUNT;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_ALIAS;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_EVM_ADDRESS;

import java.util.Optional;
import org.hiero.mirror.common.domain.entity.Entity;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface EntityRepository extends CrudRepository<Entity, Long> {

    @Caching(
            cacheable = {
                @Cacheable(cacheNames = CACHE_NAME, cacheManager = CACHE_MANAGER_ENTITY, unless = "#result == null"),
                @Cacheable(
                        cacheNames = CACHE_NAME,
                        cacheManager = CACHE_MANAGER_SYSTEM_ACCOUNT,
                        condition =
                                "#entityId < 1000 && !T(org.hiero.mirror.web3.common.ContractCallContext).isBalanceCallSafe()",
                        unless = "#result == null")
            })
    Optional<Entity> findByIdAndDeletedIsFalse(Long entityId);

    @Cacheable(
            cacheNames = CACHE_NAME_EVM_ADDRESS,
            cacheManager = CACHE_MANAGER_ENTITY,
            key = "@spelHelper.hashCode(#alias)",
            unless = "#result == null")
    @Query(value = "select * from entity where evm_address = :alias and deleted is not true")
    Optional<Entity> findByEvmAddressAndDeletedIsFalse(byte[] alias);

    @Cacheable(
            cacheNames = CACHE_NAME_ALIAS,
            cacheManager = CACHE_MANAGER_ENTITY,
            key = "@spelHelper.hashCode(#alias)",
            unless = "#result == null")
    @Query(value = """
            select *
            from entity
            where (evm_address = :alias or alias = :alias) and deleted is not true
            """)
    Optional<Entity> findByEvmAddressOrAliasAndDeletedIsFalse(byte[] alias);

    /**
     * Retrieves the most recent state of an entity by its evm address up to a given block timestamp.
     *
     * @param evmAddress      the evm address of the entity to be retrieved.
     * @param blockTimestamp  the block timestamp used to filter the results.
     * @return an Optional containing the entity's state at the specified timestamp.
     *         If there is no record found for the given criteria, an empty Optional is returned.
     */
    @Query(value = """
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
            )
            union all
            (
                select *
                from entity_history eh
                where lower(eh.timestamp_range) <= :blockTimestamp
                and eh.id = (select id from entity_cte)
                order by lower(eh.timestamp_range) desc
                limit 1
            )
            order by timestamp_range desc
            limit 1
            """)
    Optional<Entity> findActiveByEvmAddressAndTimestamp(byte[] evmAddress, long blockTimestamp);

    /**
     * Retrieves the most recent state of an entity by its alias up to a given block timestamp.
     *
     * @param alias           the alias of the entity to be retrieved.
     * @param blockTimestamp  the block timestamp used to filter the results.
     * @return an Optional containing the entity's state at the specified timestamp.
     *         If there is no record found for the given criteria, an empty Optional is returned.
     */
    @Query(value = """
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
            )
            union all
            (
                select *
                from entity_history eh
                where lower(eh.timestamp_range) <= :blockTimestamp
                and eh.id = (select id from entity_cte)
                order by lower(eh.timestamp_range) desc
                limit 1
            )
            order by timestamp_range desc
            limit 1
            """)
    Optional<Entity> findActiveByEvmAddressOrAliasAndTimestamp(byte[] alias, long blockTimestamp);

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
    @Query(value = """
                    (
                        select *
                        from entity
                        where id = :id and lower(timestamp_range) <= :blockTimestamp
                        and deleted is not true
                    )
                    union all
                    (
                        select *
                        from entity_history
                        where id = :id and lower(timestamp_range) <= :blockTimestamp
                        and deleted is not true
                        order by lower(timestamp_range) desc
                        limit 1
                    )
                    order by timestamp_range desc
                    limit 1
                    """)
    Optional<Entity> findActiveByIdAndTimestamp(long id, long blockTimestamp);

    @Query(value = """
                    select id
                    from entity
                    order by id desc
                    limit 1
                    """)
    Long findMaxId();
}
