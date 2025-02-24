// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txns.crypto.validators;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;

/**
 * Semantic check validation for {@link com.hederahashgraph.api.proto.java.CryptoDeleteAllowance}
 * transaction
 *
 * Copied Logic type from hedera-services. Differences with the original:
 * 1. Use abstraction for the state by introducing {@link Store} interface
 * 2. The class is stateless and the arguments are passed into the functions
 */
public class DeleteAllowanceChecks extends AllowanceChecks {

    /**
     * Validates all allowances provided in {@link
     * com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody}
     *
     * @param nftAllowances given nft serials allowances to remove
     * @param payerAccount payer for the transaction
     * @param store store
     * @return validation response
     */
    public ResponseCodeEnum deleteAllowancesValidation(
            final List<NftRemoveAllowance> nftAllowances, final Account payerAccount, final Store store) {
        var validity = validateAllowancesCount(nftAllowances);
        if (validity != OK) {
            return validity;
        }
        return validateNftDeleteAllowances(nftAllowances, payerAccount, store);
    }

    /**
     * Validates all the {@link NftRemoveAllowance}s in the {@link
     * com.hederahashgraph.api.proto.java.CryptoDeleteAllowance} transaction
     *
     * @param nftAllowances nft remove allowances
     * @param payerAccount payer for the txn
     * @param store store
     * @return
     */
    public ResponseCodeEnum validateNftDeleteAllowances(
            final List<NftRemoveAllowance> nftAllowances, final Account payerAccount, final Store store) {
        if (nftAllowances.isEmpty()) {
            return OK;
        }
        for (final var allowance : nftAllowances) {
            final var owner = Id.fromGrpcAccount(allowance.getOwner());
            final var serialNums = allowance.getSerialNumbersList();
            final Token token;
            try {
                token = store.loadPossiblyPausedToken(
                        Id.fromGrpcToken(allowance.getTokenId()).asEvmAddress());
            } catch (InvalidTransactionException e) {
                return e.getResponseCode();
            }
            if (token.isFungibleCommon()) {
                return FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
            }
            final var fetchResult = fetchOwnerAccount(owner, payerAccount, store);
            if (fetchResult.getRight() != OK) {
                return fetchResult.getRight();
            }
            final var ownerAccount = fetchResult.getLeft();
            final var tokenRel =
                    new TokenRelationshipKey(token.getId().asEvmAddress(), ownerAccount.getAccountAddress());
            if (!store.hasAssociation(tokenRel)) {
                return TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
            }
            final var validity = validateDeleteSerialNums(serialNums, token, store);
            if (validity != OK) {
                return validity;
            }
        }
        return OK;
    }

    ResponseCodeEnum validateDeleteSerialNums(final List<Long> serialNums, final Token token, final Store store) {
        if (serialNums.isEmpty()) {
            return EMPTY_ALLOWANCES;
        }
        return validateSerialNums(serialNums, token, store);
    }

    ResponseCodeEnum validateAllowancesCount(final List<NftRemoveAllowance> nftAllowances) {
        // each serial number of an NFT is considered as an allowance.
        // So for Nft allowances aggregated amount is considered for transaction limit calculation.
        // Number of serials will not be counted for allowance on account.
        return validateTotalAllowances(aggregateNftDeleteAllowances(nftAllowances));
    }

    /**
     * Gets sum of number of serials in the nft allowances. Considers duplicate serial numbers as
     * well.
     *
     * @param nftAllowances give nft allowances
     * @return number of serials
     */
    int aggregateNftDeleteAllowances(List<NftRemoveAllowance> nftAllowances) {
        int count = 0;
        for (var allowance : nftAllowances) {
            count += allowance.getSerialNumbersCount();
        }
        return count;
    }
}
