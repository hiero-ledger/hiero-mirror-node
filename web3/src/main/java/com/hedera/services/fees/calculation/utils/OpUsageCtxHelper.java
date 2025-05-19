// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.fees.calculation.utils;

import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static com.hedera.services.fees.usage.token.TokenOpsUsageUtils.TOKEN_OPS_USAGE_UTILS;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.getCryptoAllowancesList;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.getFungibleTokenAllowancesList;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.getNftApprovedForAll;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;

import com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases;
import com.hedera.services.fees.usage.token.meta.TokenMintMeta;
import com.hedera.services.hapi.fees.usage.crypto.ExtantCryptoContext;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Collections;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.Store.OnMissing;
import org.hyperledger.besu.datatypes.Address;

/**
 *  Copied Logic type from hedera-services. Differences with the original:
 *  1. Use abstraction for the state by introducing {@link Store} interface
 *  2. Remove FeeSchedule, UtilPrng, File logic
 *  3. Use HederaEvmContractAliases
 */
public class OpUsageCtxHelper {

    private final Store store;
    private final HederaEvmContractAliases hederaEvmContractAliases;

    public OpUsageCtxHelper(final Store store, final HederaEvmContractAliases hederaEvmContractAliases) {
        this.store = store;
        this.hederaEvmContractAliases = hederaEvmContractAliases;
    }

    public ExtantCryptoContext ctxForCryptoUpdate(TransactionBody txn) {
        final var op = txn.getCryptoUpdateAccount();
        final var id = op.getAccountIDToUpdate();
        final var accountOrAlias = id.getAlias().isEmpty()
                ? Address.wrap(Bytes.wrap(toEvmAddress(id)))
                : hederaEvmContractAliases.resolveForEvm(
                        Address.wrap(Bytes.wrap(id.getAlias().toByteArray())));
        final var account = store.getAccount(accountOrAlias, OnMissing.DONT_THROW);
        ExtantCryptoContext cryptoContext;
        if (!account.isEmptyAccount()) {
            cryptoContext = ExtantCryptoContext.newBuilder()
                    .setCurrentKey(asKeyUnchecked(account.getKey()))
                    .setCurrentMemo("")
                    .setCurrentExpiry(account.getExpiry())
                    .setCurrentlyHasProxy(account.getProxy() != null)
                    .setCurrentNumTokenRels(account.getNumAssociations())
                    .setCurrentMaxAutomaticAssociations(account.getMaxAutoAssociations())
                    .setCurrentCryptoAllowances(getCryptoAllowancesList(account))
                    .setCurrentTokenAllowances(getFungibleTokenAllowancesList(account))
                    .setCurrentApproveForAllNftAllowances(getNftApprovedForAll(account))
                    .build();
        } else {
            cryptoContext = ExtantCryptoContext.newBuilder()
                    .setCurrentExpiry(
                            txn.getTransactionID().getTransactionValidStart().getSeconds())
                    .setCurrentMemo("")
                    .setCurrentKey(Key.getDefaultInstance())
                    .setCurrentlyHasProxy(false)
                    .setCurrentNumTokenRels(0)
                    .setCurrentMaxAutomaticAssociations(0)
                    .setCurrentCryptoAllowances(Collections.emptyMap())
                    .setCurrentTokenAllowances(Collections.emptyMap())
                    .setCurrentApproveForAllNftAllowances(Collections.emptySet())
                    .build();
        }
        return cryptoContext;
    }

    public ExtantCryptoContext ctxForCryptoAllowance(TxnAccessor accessor) {
        final var id = accessor.getPayer();
        final var accountOrAlias = id.getAlias().isEmpty()
                ? Address.wrap(Bytes.wrap(toEvmAddress(id)))
                : hederaEvmContractAliases.resolveForEvm(
                        Address.wrap(Bytes.wrap(id.getAlias().toByteArray())));
        final var account = store.getAccount(accountOrAlias, OnMissing.DONT_THROW);
        ExtantCryptoContext cryptoContext;
        if (!account.isEmptyAccount()) {
            cryptoContext = ExtantCryptoContext.newBuilder()
                    .setCurrentKey(asKeyUnchecked(account.getKey()))
                    .setCurrentMemo("")
                    .setCurrentExpiry(account.getExpiry())
                    .setCurrentlyHasProxy(account.getProxy() != null)
                    .setCurrentNumTokenRels(account.getNumAssociations())
                    .setCurrentMaxAutomaticAssociations(account.getMaxAutoAssociations())
                    .setCurrentCryptoAllowances(getCryptoAllowancesList(account))
                    .setCurrentTokenAllowances(getFungibleTokenAllowancesList(account))
                    .setCurrentApproveForAllNftAllowances(getNftApprovedForAll(account))
                    .build();
        } else {
            cryptoContext = ExtantCryptoContext.newBuilder()
                    .setCurrentExpiry(accessor.getTxn()
                            .getTransactionID()
                            .getTransactionValidStart()
                            .getSeconds())
                    .setCurrentMemo("")
                    .setCurrentKey(Key.getDefaultInstance())
                    .setCurrentlyHasProxy(false)
                    .setCurrentNumTokenRels(0)
                    .setCurrentMaxAutomaticAssociations(0)
                    .setCurrentCryptoAllowances(Collections.emptyMap())
                    .setCurrentTokenAllowances(Collections.emptyMap())
                    .setCurrentApproveForAllNftAllowances(Collections.emptySet())
                    .build();
        }
        return cryptoContext;
    }

    public TokenMintMeta metaForTokenMint(TxnAccessor accessor) {
        return TOKEN_OPS_USAGE_UTILS.tokenMintUsageFrom(accessor.getTxn(), accessor.getSubType());
    }
}
