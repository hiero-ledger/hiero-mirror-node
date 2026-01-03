// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_TOKEN;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_NFT;

import java.util.Optional;
import org.hiero.mirror.common.domain.token.AbstractNft;
import org.hiero.mirror.common.domain.token.Nft;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface NftRepository extends CrudRepository<Nft, AbstractNft.Id> {

    @Override
    @Cacheable(cacheNames = CACHE_NAME_NFT, cacheManager = CACHE_MANAGER_TOKEN, unless = "#result == null")
    Optional<Nft> findById(AbstractNft.Id id);

    @Query(
            value = "select n.* from Nft n "
                    + "left join Entity e on e.id = n.token_id "
                    + "where n.token_id=:tokenId and n.serial_number=:serialNumber "
                    + "and n.deleted is false and e.deleted is not true",
            nativeQuery = true)
    Optional<Nft> findActiveById(long tokenId, long serialNumber);

    /**
     * Retrieves the most recent state of an nft by its ID up to a given block timestamp. The method considers both the
     * current state of the nft and its historical states and returns the one that was valid just before or equal to the
     * provided block timestamp.
     *
     * @param tokenId        the token id of the nft to be retrieved.
     * @param serialNumber   the serial number of the nft to be retrieved.
     * @param blockTimestamp the block timestamp used to filter the results.
     * @return an Optional containing the nft's state at the specified timestamp. If there is no record found for the
     * given criteria, an empty Optional is returned.
     */
    @Query(value = """
            select n.*
            from (
                (
                    select *
                    from nft
                    where token_id = :tokenId
                        and serial_number = :serialNumber
                        and lower(timestamp_range) <= :blockTimestamp
                        and deleted is not true
                )
                union all
                (
                    select *
                    from nft_history
                    where token_id = :tokenId
                        and serial_number = :serialNumber
                        and lower(timestamp_range) <= :blockTimestamp
                        and deleted is not true
                    order by lower(timestamp_range) desc
                    limit 1
                )
            ) as n
            join entity e on e.id = n.token_id
            where (e.deleted is not true or lower(e.timestamp_range) > :blockTimestamp)
            order by n.timestamp_range desc
            limit 1
            """, nativeQuery = true)
    Optional<Nft> findActiveByIdAndTimestamp(long tokenId, long serialNumber, long blockTimestamp);

    @Query(
            value = "select count(*) from Nft n "
                    + "join Entity e on e.id = n.token_id "
                    + "where n.account_id=:accountId and n.deleted is false and e.deleted is not true",
            nativeQuery = true)
    long countByAccountIdNotDeleted(Long accountId);

    /**
     * Retrieves the most recent state of number of associated tokens (and if their balance is positive) by accountId up
     * to a given block timestamp. The method considers both the current state of the token account and its historical
     * states and returns the one that was valid just before or equal to the provided block timestamp.
     *
     * @param accountId      the ID of the account
     * @param blockTimestamp the block timestamp used to filter the results.
     * @return the number of nfts the accountId owns at the specified timestamp.
     */
    @Query(value = """
            select count(*)
            from (
                (
                    select token_id
                    from nft
                    where account_id = :accountId
                        and timestamp_range @> :blockTimestamp
                        and deleted is not true
                )
                union all
                (
                    select token_id
                    from nft_history
                    where account_id = cast(:accountId as bigint) -- cast to utilize the btree_gist index
                        and timestamp_range @> :blockTimestamp
                        and deleted is not true
                )
            ) as n
            left join entity e on e.id = n.token_id
            where (e.deleted is not true or lower(e.timestamp_range) > :blockTimestamp)
            """, nativeQuery = true)
    long countByAccountIdAndTimestampNotDeleted(long accountId, long blockTimestamp);

    /**
     * Retrieves the most recent state of nft balance by accountId and tokenId up to a given block timestamp. The method
     * considers both the current state of the token account and its historical states and returns the one that was
     * valid just before or equal to the provided block timestamp.
     *
     * @param accountId      the ID of the account
     * @param tokenId        the ID of the nft
     * @param blockTimestamp the block timestamp used to filter the results.
     * @return the number of nft serial numbers the accountId owns at the specified timestamp for specific tokenId.
     */
    @Query(value = """
            select count(*)
            from (
                (
                    select token_id
                    from nft
                    where account_id = :accountId
                        and token_id = :tokenId
                        and timestamp_range @> :blockTimestamp
                        and deleted is not true
                )
                union all
                (
                    select token_id
                    from nft_history
                    where account_id = cast(:accountId as bigint) -- cast to utilize the btree_gist index
                        and token_id = :tokenId
                        and timestamp_range @> :blockTimestamp
                        and deleted is not true
                )
                ) as n
            join entity e on e.id = n.token_id
            where (e.deleted is not true or lower(e.timestamp_range) > :blockTimestamp)
            """, nativeQuery = true)
    Optional<Long> nftBalanceByAccountIdTokenIdAndTimestamp(long accountId, long tokenId, long blockTimestamp);

    /**
     * Finds the historical token total supply for a given token ID based on a specific block timestamp. This method
     * calculates the historical supply by getting the sum of all not deleted nft serials for this specific timestamp
     * range
     *
     * @param tokenId        the ID of the token
     * @param blockTimestamp the block timestamp used to filter the results.
     * @return the token's total supply at the specified timestamp.
     */
    @Query(value = """
            select count(*)
            from nft as n
            left join entity as e on e.id = n.token_id
            where token_id = :tokenId and
              n.created_timestamp <= :blockTimestamp and
              (n.deleted is false or lower(n.timestamp_range) > :blockTimestamp) and
              (e.deleted is not true or lower(e.timestamp_range) > :blockTimestamp)
            """, nativeQuery = true)
    long findNftTotalSupplyByTokenIdAndTimestamp(long tokenId, long blockTimestamp);
}
