// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txns.crypto;

import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.fetchOwnerAccount;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.updateSpender;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_SPENDER_ID;

import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.FcTokenAllowanceId;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.Store.OnMissing;

/**
 *  Copied Logic type from hedera-services. Differences with the original:
 *  1. Use abstraction for the state by introducing {@link Store} interface
 *  2. Remove AccountStore, TypedTokenStore and GlobalDynamicProperties
 *  3. Remove validateAllowanceLimitsOn since mirror-node properties do not have allowance limit
 *  4. Using exception from {@link org.hiero.mirror.web3.evm.store.StoreImpl} when entity is missing from the state
 *  5. accountsChanged and nftsTouched are passed as method parameters
 *  6. We pass some of the fields as function parameters in order to keep the class stateless
 */
public class ApproveAllowanceLogic {

    public void approveAllowance(
            final Store store,
            final Map<Long, Account> accountsChanged,
            final Map<NftId, UniqueToken> nftsTouched,
            final List<CryptoAllowance> cryptoAllowances,
            final List<TokenAllowance> tokenAllowances,
            final List<NftAllowance> nftAllowances,
            final AccountID payer) {

        /* --- Use models --- */
        final Id payerId = Id.fromGrpcAccount(payer);
        final var payerAccount = store.getAccount(payerId.asEvmAddress(), OnMissing.THROW);

        /* --- Do the business logic --- */
        applyCryptoAllowances(store, accountsChanged, cryptoAllowances, payerAccount);
        applyFungibleTokenAllowances(store, accountsChanged, tokenAllowances, payerAccount);
        applyNftAllowances(store, accountsChanged, nftsTouched, nftAllowances, payerAccount);

        /* --- Persist the entities --- */
        for (final var nft : nftsTouched.values()) {
            store.updateUniqueToken(nft);
        }
        for (final var account : accountsChanged.entrySet()) {
            store.updateAccount(account.getValue());
        }
    }

    /**
     * Applies all changes needed for Crypto allowances from the transaction. If the spender already
     * has an allowance, the allowance value will be replaced with values from transaction
     *
     * @param cryptoAllowances
     * @param payerAccount
     */
    private void applyCryptoAllowances(
            final Store store,
            final Map<Long, Account> accountsChanged,
            final List<CryptoAllowance> cryptoAllowances,
            Account payerAccount) {
        if (cryptoAllowances.isEmpty()) {
            return;
        }
        for (final var allowance : cryptoAllowances) {
            final var owner = allowance.getOwner();
            var accountToApprove = fetchOwnerAccount(owner, payerAccount, store, accountsChanged);
            final var cryptoMap = new TreeMap<>(accountToApprove.getCryptoAllowances());

            final var spender = Id.fromGrpcAccount(allowance.getSpender());
            final var amount = allowance.getAmount();
            if (cryptoMap.containsKey(spender.asEntityNum()) && amount == 0) {
                // spender need not be validated as being a valid account when removing allowances,
                // since it might be deleted and allowance is being removed by owner if it exists in map.
                removeEntity(accountsChanged, cryptoMap, spender, accountToApprove);
            }
            if (amount > 0) {
                // To add allowances spender should be validated as being a valid account
                store.loadAccountOrFailWith(spender.asEvmAddress(), INVALID_ALLOWANCE_SPENDER_ID);
                cryptoMap.put(spender.asEntityNum(), amount);
                accountToApprove = accountToApprove.setCryptoAllowance(cryptoMap);
                accountsChanged.put(accountToApprove.getId().num(), accountToApprove);
            }
        }
    }

    private void removeEntity(
            final Map<Long, Account> accountsChanged,
            final SortedMap<EntityNum, Long> cryptoMap,
            final Id spender,
            Account accountToApprove) {
        cryptoMap.remove(spender.asEntityNum());
        accountToApprove = accountToApprove.setCryptoAllowance(cryptoMap);
        accountsChanged.put(accountToApprove.getId().num(), accountToApprove);
    }

    /**
     * Applies all changes needed for fungible token allowances from the transaction.If the key
     * {token, spender} already has an allowance, the allowance value will be replaced with values
     * from transaction
     *
     * @param tokenAllowances
     * @param payerAccount
     */
    private void applyFungibleTokenAllowances(
            final Store store,
            final Map<Long, Account> accountsChanged,
            final List<TokenAllowance> tokenAllowances,
            Account payerAccount) {
        if (tokenAllowances.isEmpty()) {
            return;
        }
        for (final var allowance : tokenAllowances) {
            final var owner = allowance.getOwner();
            var accountToApprove = fetchOwnerAccount(owner, payerAccount, store, accountsChanged);
            final var tokensMap = new TreeMap<>(accountToApprove.getFungibleTokenAllowances());

            final var spender = Id.fromGrpcAccount(allowance.getSpender());
            final var amount = allowance.getAmount();
            final var tokenId = allowance.getTokenId();

            final var key = FcTokenAllowanceId.from(EntityNum.fromTokenId(tokenId), spender.asEntityNum());
            if (tokensMap.containsKey(key) && amount == 0) {
                // spender need not be validated as being a valid account when removing allowances,
                // since it might be deleted and allowance is being removed by owner if it exists in map.
                removeTokenEntity(accountsChanged, key, tokensMap, accountToApprove);
            }
            if (amount > 0) {
                // To add allowances spender should be validated as being a valid account
                store.loadAccountOrFailWith(spender.asEvmAddress(), INVALID_ALLOWANCE_SPENDER_ID);

                tokensMap.put(key, amount);
                accountToApprove = accountToApprove.setFungibleTokenAllowances(tokensMap);
                accountsChanged.put(accountToApprove.getId().num(), accountToApprove);
            }
        }
    }

    /**
     * Applies all changes needed for NFT allowances from the transaction. If the key{tokenNum,
     * spenderNum} doesn't exist in the map the allowance will be inserted. If the key exists,
     * existing allowance values will be replaced with new allowances given in operation
     *
     * @param nftAllowances
     * @param payerAccount
     */
    protected void applyNftAllowances(
            final Store store,
            final Map<Long, Account> accountsChanged,
            final Map<NftId, UniqueToken> nftsTouched,
            final List<NftAllowance> nftAllowances,
            final Account payerAccount) {
        if (nftAllowances.isEmpty()) {
            return;
        }
        for (final var allowance : nftAllowances) {
            final var owner = allowance.getOwner();
            var approvingAccount = fetchOwnerAccount(owner, payerAccount, store, accountsChanged);
            final var spenderId = Id.fromGrpcAccount(allowance.getSpender());
            final var tokenId = Id.fromGrpcToken(allowance.getTokenId());

            if (allowance.hasApprovedForAll()) {
                final var approveForAllNfts = new TreeSet<>(approvingAccount.getApproveForAllNfts());
                final var key = FcTokenAllowanceId.from(tokenId.asEntityNum(), spenderId.asEntityNum());
                if (allowance.getApprovedForAll().getValue()) {
                    // Validate the spender/operator account
                    store.loadAccountOrFailWith(spenderId.asEvmAddress(), INVALID_ALLOWANCE_SPENDER_ID);
                    approveForAllNfts.add(key);
                } else {
                    // Need not validate anything here to revoke the approval
                    approveForAllNfts.remove(key);
                }
                final var accountWithUpdatedApproveForAllNfts =
                        approvingAccount.setApproveForAllNfts(approveForAllNfts);
                if (!accountWithUpdatedApproveForAllNfts.equals(approvingAccount)) {
                    accountsChanged.put(approvingAccount.getId().num(), accountWithUpdatedApproveForAllNfts);
                }
            }

            if (allowance.getSerialNumbersCount() > 0) {
                // To add allowance for any serials, need to validate spender
                store.loadAccountOrFailWith(spenderId.asEvmAddress(), INVALID_ALLOWANCE_SPENDER_ID);
            }

            final var nfts = updateSpender(
                    store, approvingAccount.getId(), spenderId, tokenId, allowance.getSerialNumbersList());
            for (final var nft : nfts) {
                nftsTouched.put(nft.getNftId(), nft);
            }
        }
    }

    private void removeTokenEntity(
            final Map<Long, Account> accountsChanged,
            final FcTokenAllowanceId key,
            final SortedMap<FcTokenAllowanceId, Long> tokensMap,
            Account accountToApprove) {
        tokensMap.remove(key);
        accountToApprove = accountToApprove.setFungibleTokenAllowances(tokensMap);
        accountsChanged.put(accountToApprove.getId().num(), accountToApprove);
    }
}
