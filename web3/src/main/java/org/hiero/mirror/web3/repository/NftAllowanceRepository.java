// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_TOKEN;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_NFT_ALLOWANCE;

import java.util.List;
import org.hiero.mirror.common.domain.entity.AbstractNftAllowance.Id;
import org.hiero.mirror.common.domain.entity.NftAllowance;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface NftAllowanceRepository extends CrudRepository<NftAllowance, Id> {

    @Cacheable(cacheNames = CACHE_NAME_NFT_ALLOWANCE, cacheManager = CACHE_MANAGER_TOKEN, unless = "#result == null")
    List<NftAllowance> findByOwnerAndApprovedForAllIsTrue(long owner);

    /**
     * Retrieves the most recent state of nft allowances by its owner up to a given block timestamp.
     * The method considers both the current state of the nft allowance and its historical states
     * and returns the latest valid just before or equal to the provided block timestamp.
     *
     * @param owner the ID of the owner
     * @param blockTimestamp  the block timestamp used to filter the results.
     * @return List containing the nft allowances at the specified timestamp.
     */
    @Query(value = """
                    with nft_allowances as (
                        select *
                        from (
                            select *,
                                row_number() over (
                                    partition by spender, token_id
                                    order by lower(timestamp_range) desc
                                ) as row_number
                            from (
                                select *
                                from nft_allowance
                                where owner = :owner
                                    and approved_for_all = true
                                    and lower(timestamp_range) <= :blockTimestamp
                                union all
                                select *
                                from nft_allowance_history
                                where owner = :owner
                                    and approved_for_all = true
                                    and lower(timestamp_range) <= :blockTimestamp
                            ) as nft_allowance_history
                        ) as row_numbered_data
                        where row_number = 1
                    )
                    select *
                    from nft_allowances
                    order by timestamp_range desc
                    """, nativeQuery = true)
    List<NftAllowance> findByOwnerAndTimestampAndApprovedForAllIsTrue(long owner, long blockTimestamp);
}
