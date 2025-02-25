// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txns.crypto.validators;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.HashSet;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Validations for {@link com.hederahashgraph.api.proto.java.CryptoApproveAllowance} transaction
 * allowances
 *
 * Copied Logic type from hedera-services. Differences with the original:
 * 1. Use abstraction for the state by introducing {@link Store} interface
 * 2. The class is stateless and the arguments are passed into the functions
 */
public class AllowanceChecks {

    /**
     * Each serial number in an {@code NftAllowance} is considered as an allowance.
     *
     * @param nftAllowances a list of NFT individual allowances
     * @return the number of mentioned serial numbers
     */
    public static int aggregateNftAllowances(List<NftAllowance> nftAllowances) {
        int nftAllowancesTotal = 0;
        for (var allowances : nftAllowances) {
            var serials = allowances.getSerialNumbersList();
            if (!serials.isEmpty()) {
                nftAllowancesTotal += serials.size();
            } else {
                nftAllowancesTotal++;
            }
        }
        return nftAllowancesTotal;
    }

    ResponseCodeEnum validateTotalAllowances(final int totalAllowances) {
        // 20 is the default maxAllowanceLimitPerTransaction in hedera-services
        if (exceedsTxnLimit(totalAllowances, 20)) {
            return MAX_ALLOWANCES_EXCEEDED;
        }
        if (emptyAllowances(totalAllowances)) {
            return EMPTY_ALLOWANCES;
        }
        return OK;
    }

    /**
     * Validates serial numbers for {@link NftAllowance}
     *
     * @param serialNums given serial numbers in the {@link
     *     com.hederahashgraph.api.proto.java.CryptoApproveAllowance} operation
     * @param token token for which allowance is related to
     * @return response code after validation
     */
    ResponseCodeEnum validateSerialNums(final List<Long> serialNums, final Token token, final Store store) {
        final var serialsSet = new HashSet<>(serialNums);
        for (var serial : serialsSet) {
            if (serial <= 0) {
                return INVALID_TOKEN_NFT_SERIAL_NUMBER;
            }
            try {
                final var nftId = new NftId(
                        token.getId().shard(),
                        token.getId().realm(),
                        token.getId().num(),
                        serial);
                store.getUniqueToken(nftId, OnMissing.THROW);
            } catch (InvalidTransactionException ex) {
                return INVALID_TOKEN_NFT_SERIAL_NUMBER;
            }
        }

        return OK;
    }

    boolean exceedsTxnLimit(final int totalAllowances, final int maxLimit) {
        return totalAllowances > maxLimit;
    }

    boolean emptyAllowances(final int totalAllowances) {
        return totalAllowances == 0;
    }

    Pair<Account, ResponseCodeEnum> fetchOwnerAccount(final Id owner, final Account payerAccount, final Store store) {
        if (owner.equals(Id.DEFAULT) || owner.equals(payerAccount.getId())) {
            return Pair.of(payerAccount, OK);
        } else {
            try {
                return Pair.of(store.getAccount(owner.asEvmAddress(), OnMissing.THROW), OK);
            } catch (InvalidTransactionException ex) {
                return Pair.of(payerAccount, INVALID_ALLOWANCE_OWNER_ID);
            }
        }
    }
}
