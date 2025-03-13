// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txns.crypto.helpers;

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.services.hapi.fees.usage.crypto.AllowanceId;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *  Copied Logic type from hedera-services. Differences with the original:
 *  1. Use abstraction for the state by introducing {@link Store} interface
 *  2. Remove unused methods
 *  3. We pass some of the fields as function parameters in order to keep the class stateless
 */
public class AllowanceHelpers {
    private AllowanceHelpers() {
        throw new IllegalStateException("Utility class");
    }

    public static Set<AllowanceId> getNftApprovedForAll(final Account account) {
        if (!account.getApproveForAllNfts().isEmpty()) {
            Set<AllowanceId> nftAllowances = new HashSet<>();
            for (var a : account.getApproveForAllNfts()) {
                nftAllowances.add(new AllowanceId(
                        a.getTokenNum().toTokenId(), a.getSpenderNum().scopedAccountWith()));
            }
            return nftAllowances;
        }
        return Collections.emptySet();
    }

    public static Map<AllowanceId, Long> getFungibleTokenAllowancesList(final Account account) {
        if (!account.getFungibleTokenAllowances().isEmpty()) {
            Map<AllowanceId, Long> tokenAllowances = new HashMap<>();
            for (var a : account.getFungibleTokenAllowances().entrySet()) {
                tokenAllowances.put(
                        new AllowanceId(
                                a.getKey().getTokenNum().toTokenId(),
                                a.getKey().getSpenderNum().scopedAccountWith()),
                        a.getValue());
            }
            return tokenAllowances;
        }
        return Collections.emptyMap();
    }

    public static Map<EntityNum, Long> getCryptoAllowancesList(final Account account) {
        if (!account.getCryptoAllowances().isEmpty()) {
            Map<EntityNum, Long> cryptoAllowances = new HashMap<>();

            for (var a : account.getCryptoAllowances().entrySet()) {
                cryptoAllowances.put(a.getKey(), a.getValue());
            }
            return cryptoAllowances;
        }
        return Collections.emptyMap();
    }
    /**
     * Returns owner account to be considered for the allowance changes. If the owner is missing in
     * allowance, considers payer of the transaction as the owner. This is same for
     * CryptoApproveAllowance and CryptoDeleteAllowance transaction. Looks at entitiesChanged map
     * before fetching from store for performance.
     *
     * @param owner given owner
     * @param payerAccount given payer for the transaction
     * @param store store
     * @param entitiesChanged map of all entities that are changed
     * @return owner account
     */
    public static Account fetchOwnerAccount(
            final AccountID owner,
            final Account payerAccount,
            final Store store,
            final Map<Long, Account> entitiesChanged) {
        final var ownerId = Id.fromGrpcAccount(owner);
        if (owner.equals(AccountID.getDefaultInstance())
                || owner.equals(payerAccount.getId().asGrpcAccount())) {
            return entitiesChanged.getOrDefault(payerAccount.getId().num(), payerAccount);
        } else if (entitiesChanged.containsKey(ownerId.num())) {
            return entitiesChanged.get(ownerId.num());
        } else {
            return store.loadAccountOrFailWith(ownerId.asEvmAddress(), INVALID_ALLOWANCE_OWNER_ID);
        }
    }

    public static Account fetchOwnerAccount(final AccountID owner, final Account payerAccount, final Store store) {
        return fetchOwnerAccount(owner, payerAccount, store, Collections.emptyMap());
    }

    /**
     * Updates the Spender of each NFT serial
     *
     * @param store The store to load UniqueToken and Token models to validate and update
     *     the spender.
     * @param ownerId The owner Id of the NFT serials
     * @param spenderId The spender to be set for the NFT serials
     * @param tokenId The token ID of the NFT type.
     * @param serialNums The serial numbers of the NFT type to update the spender.
     * @return A list of UniqueTokens that we updated.
     */
    public static List<UniqueToken> updateSpender(
            final Store store, final Id ownerId, final Id spenderId, final Id tokenId, final List<Long> serialNums) {
        if (serialNums.isEmpty()) {
            return Collections.emptyList();
        }

        final var nfts = new ArrayList<UniqueToken>();
        final var serialsSet = new HashSet<>(serialNums);
        for (var serialNum : serialsSet) {
            final var nftId = new NftId(tokenId.shard(), tokenId.realm(), tokenId.num(), serialNum);
            final var nft = store.getUniqueToken(nftId, OnMissing.THROW);
            final var token = store.loadPossiblyPausedToken(tokenId.asEvmAddress());
            validateTrue(validOwner(nft, ownerId, token), SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);
            nfts.add(nft.setSpender(spenderId));
        }
        return nfts;
    }

    /**
     * Checks the owner of token is treasury or the owner id given in allowance. If not, considers
     * as an invalid owner and returns false.
     *
     * @param nft given nft
     * @param ownerId owner given in allowance
     * @param token token for which nft belongs to
     * @return whether the owner is valid
     */
    public static boolean validOwner(final UniqueToken nft, final Id ownerId, final Token token) {
        final var listedOwner = nft.getOwner();
        return Id.DEFAULT.equals(listedOwner)
                ? ownerId.equals(token.getTreasury().getId())
                : listedOwner.equals(ownerId);
    }
}
