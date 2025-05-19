// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txns.crypto;

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.services.store.models.Id.fromGrpcAccount;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.fetchOwnerAccount;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.validOwner;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;

import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.UniqueToken;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import java.util.ArrayList;
import java.util.List;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.Store.OnMissing;

/**
 *  Copied Logic type from hedera-services. Differences with the original:
 *  1. Use abstraction for the state by introducing {@link Store} interface
 *  2. Remove AccountStore and TypedTokenStore
 *  3. Using exception from {@link org.hiero.mirror.web3.evm.store.StoreImpl} when entity is missing from the state
 *  4. We pass some of the fields as function parameters in order to keep the class stateless
 */
public class DeleteAllowanceLogic {

    public void deleteAllowance(
            final Store store,
            final List<UniqueToken> nftsTouched,
            List<NftRemoveAllowance> nftAllowancesList,
            final AccountID payer) {

        // --- Load models ---
        final Id payerId = fromGrpcAccount(payer);
        final var payerAccount = store.getAccount(payerId.asEvmAddress(), OnMissing.THROW);
        // --- Do the business logic ---
        deleteNftSerials(store, nftsTouched, nftAllowancesList, payerAccount);

        // --- Persist the owner accounts and nfts ---
        for (final var nft : nftsTouched) {
            store.updateUniqueToken(nft);
        }
    }

    /**
     *
     * Clear spender on the provided nft serials. If the owner is not provided in any allowance,
     * considers payer of the transaction as owner while checking if nft is owned by owner.
     * This version of the method is using {@link Store} for the transaction state
     *
     * @param store
     * @param nftsTouched
     * @param nftAllowances
     * @param payerAccount
     */
    private void deleteNftSerials(
            final Store store,
            final List<UniqueToken> nftsTouched,
            final List<NftRemoveAllowance> nftAllowances,
            final Account payerAccount) {
        if (nftAllowances.isEmpty()) {
            return;
        }
        final var nfts = new ArrayList<UniqueToken>();
        for (final var allowance : nftAllowances) {
            final var serialNums = allowance.getSerialNumbersList();
            final var tokenId = Id.fromGrpcToken(allowance.getTokenId());
            final var owner = fetchOwnerAccount(allowance.getOwner(), payerAccount, store);
            final var token = store.loadPossiblyPausedToken(tokenId.asEvmAddress());
            for (final var serial : serialNums) {
                final var nftId = new NftId(tokenId.shard(), tokenId.realm(), tokenId.num(), serial);
                final var nft = store.getUniqueToken(nftId, OnMissing.THROW);
                validateTrue(validOwner(nft, owner.getId(), token), SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);
                nfts.add(nft.setSpender(Id.DEFAULT));
            }
            nftsTouched.addAll(nfts);
            nfts.clear();
        }
    }
}
