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
     * Retrieves the state of the nft allowances by their owner at a given block timestamp.
     * The method considers both the current state of the nft allowance and its historical states,
     * selecting the row whose timestamp_range contains the block timestamp for each spender/token, and
     * returns only those that were still approved for all at that point in time. Allowances whose
     * approval had been revoked at or before the block timestamp are excluded.
     *
     * @param owner the ID of the owner
     * @param blockTimestamp  the block timestamp used to filter the results.
     * @return List containing the nft allowances at the specified timestamp.
     */
    @Query(value = """
                    (
                        select *
                        from nft_allowance
                        where owner = :owner
                            and approved_for_all = true
                            and lower(timestamp_range) <= :blockTimestamp
                    )
                    union all
                    (
                        select *
                        from nft_allowance_history
                        where owner = :owner
                            and approved_for_all = true
                            and timestamp_range @> :blockTimestamp
                    )
                    order by timestamp_range desc
                    """, nativeQuery = true)
    List<NftAllowance> findByOwnerAndTimestampAndApprovedForAllIsTrue(long owner, long blockTimestamp);
}
