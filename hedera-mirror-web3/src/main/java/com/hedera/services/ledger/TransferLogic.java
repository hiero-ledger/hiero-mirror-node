// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.ledger;

import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hedera.services.utils.EntityNum.fromEvmAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AMOUNT_EXCEEDS_ALLOWANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;

import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.contract.EntityAddressSequencer;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.FcTokenAllowanceId;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.txns.crypto.AutoCreationLogic;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

/**
 * Copied Logic type from hedera-services. Differences with the original:
 *  1. Use abstraction for the state by introducing {@link Store} interface
 *  2. Removed topLevelPayer related logic
 *  3. Removed SideEffectsTracker, RecordsHistorian, TransactionContext, FeeDistribution
 * */
public class TransferLogic {
    private final AutoCreationLogic autoCreationLogic;
    private final MirrorEvmContractAliases mirrorEvmContractAliases;

    public TransferLogic(
            final AutoCreationLogic autoCreationLogic, final MirrorEvmContractAliases mirrorEvmContractAliases) {
        this.autoCreationLogic = autoCreationLogic;
        this.mirrorEvmContractAliases = mirrorEvmContractAliases;
    }

    public void doZeroSum(
            final List<BalanceChange> changes,
            Store store,
            EntityAddressSequencer ids,
            HederaTokenStore hederaTokenStore) {
        var validity = OK;
        for (final var change : changes) {
            // If the change consists of any repeated aliases, replace the alias with the account
            // number
            replaceAliasWithIdIfExisting(change);

            // create a new account for alias when the no account is already created using the alias
            if (change.hasAlias()) {
                if (autoCreationLogic == null) {
                    throw new IllegalStateException(
                            "Cannot auto-create account from " + change + " with null autoCreationLogic");
                }
                final var result = autoCreationLogic.create(
                        change,
                        Timestamp.newBuilder()
                                .setSeconds(Instant.now().getEpochSecond())
                                .build(),
                        store,
                        ids,
                        changes);
                validity = result.getKey();
                if (validity == OK && (change.isForToken())) {
                    validity = hederaTokenStore.tryTokenChange(change);
                }
            } else if (change.isForToken()) {
                validity = hederaTokenStore.tryTokenChange(change);
            }
            if (validity != OK) {
                break;
            }
        }

        if (validity == OK) {
            adjustBalancesAndAllowances(changes, store);
        } else {
            throw new InvalidTransactionException(validity);
        }
    }

    private void adjustBalancesAndAllowances(final List<BalanceChange> changes, Store store) {
        for (final var change : changes) {
            final var accountId = change.accountId();
            if (change.isForHbar()) {
                final var newBalance = change.getNewBalance();
                Address accountAddress = asTypedEvmAddress(accountId);
                Account account = store.getAccount(accountAddress, OnMissing.THROW);
                account = account.setBalance(newBalance);
                store.updateAccount(account);
                if (change.isApprovedAllowance()) {
                    adjustCryptoAllowance(change, accountId, store);
                }
            } else if (change.isApprovedAllowance() && change.isForFungibleToken()) {
                adjustFungibleTokenAllowance(change, accountId, store);
            } else if (change.isForNft()) {
                // wipe the allowance on this uniqueToken
                UniqueToken uniqueToken = store.getUniqueToken(change.nftId(), OnMissing.THROW);
                uniqueToken = uniqueToken.setSpender(Id.DEFAULT);
                store.updateUniqueToken(uniqueToken);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void adjustCryptoAllowance(final BalanceChange change, final AccountID ownerID, final Store store) {
        final var payerNum = EntityNum.fromAccountId(change.getPayerID());
        Account account = store.getAccount(asTypedEvmAddress(ownerID), OnMissing.THROW);
        final var hbarAllowances = new TreeMap<>((Map<EntityNum, Long>) account.getCryptoAllowances());
        final var currentAllowance = hbarAllowances.get(payerNum);
        if (currentAllowance == null) {
            throw new InvalidTransactionException(SPENDER_DOES_NOT_HAVE_ALLOWANCE);
        }
        final var newAllowance = currentAllowance + change.getAllowanceUnits();

        if (newAllowance < 0L) {
            throw new InvalidTransactionException(AMOUNT_EXCEEDS_ALLOWANCE);
        } else if (newAllowance != 0) {
            hbarAllowances.put(payerNum, newAllowance);
        } else {
            hbarAllowances.remove(payerNum);
        }
        account = account.setCryptoAllowance(hbarAllowances);
        store.updateAccount(account);
    }

    @SuppressWarnings("unchecked")
    private void adjustFungibleTokenAllowance(final BalanceChange change, final AccountID ownerID, final Store store) {
        final var allowanceId = FcTokenAllowanceId.from(
                EntityNum.fromLong(change.getToken().num()), EntityNum.fromAccountId(change.getPayerID()));
        Account account = store.getAccount(asTypedEvmAddress(ownerID), OnMissing.THROW);
        final var fungibleAllowances =
                new TreeMap<>((Map<FcTokenAllowanceId, Long>) account.getFungibleTokenAllowances());
        final var currentAllowance = fungibleAllowances.get(allowanceId);
        if (currentAllowance == null) {
            throw new InvalidTransactionException(SPENDER_DOES_NOT_HAVE_ALLOWANCE);
        }
        final var newAllowance = currentAllowance + change.getAllowanceUnits();
        if (newAllowance == 0L) {
            fungibleAllowances.remove(allowanceId);
        } else if (newAllowance < 0L) {
            throw new InvalidTransactionException(AMOUNT_EXCEEDS_ALLOWANCE);
        } else {
            fungibleAllowances.put(allowanceId, newAllowance);
        }
        account = account.setFungibleTokenAllowances(fungibleAllowances);
        store.updateAccount(account);
    }

    /**
     * Checks if the alias is a known alias i.e, if the alias is already used in any cryptoTransfer transaction that has
     * led to account creation
     *
     * @param change                   change that contains alias
     */
    private void replaceAliasWithIdIfExisting(final BalanceChange change) {
        final var alias = change.getNonEmptyAliasIfPresent();

        if (alias != null) {
            Address address = mirrorEvmContractAliases.resolveForEvm(Address.wrap(Bytes.wrap(alias.toByteArray())));
            final var aliasNum = address != null ? fromEvmAddress(address) : EntityNum.MISSING_NUM;
            if (aliasNum != EntityNum.MISSING_NUM) {
                change.replaceNonEmptyAliasWith(aliasNum);
            }
        }
    }
}
