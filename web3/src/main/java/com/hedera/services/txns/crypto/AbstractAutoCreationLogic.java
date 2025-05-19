// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txns.crypto;

import static com.hedera.node.app.service.evm.store.models.HederaEvmAccount.EVM_ADDRESS_SIZE;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.EMPTY_KEY;
import static com.hedera.services.utils.EntityNum.fromAccountId;
import static com.hedera.services.utils.MiscUtils.asPrimitiveKeyUnchecked;
import static com.hedera.services.utils.MiscUtils.synthAccessorFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.commons.lang3.tuple.Pair;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.account.MirrorEvmContractAliases;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.contract.EntityAddressSequencer;
import org.hyperledger.besu.datatypes.Address;

/**
 * Copied Logic type from hedera-services. Differences with the original:
 * 1. Use abstraction for the state by introducing {@link Store} interface
 * 2. Remove alias logic and pending creations, use {@link MirrorEvmContractAliases} instead
 * thus removing the List<BalanceChange> changes argument from create
 * 3. Remove SyntheticTxnFactory
 * 4. Remove UsageLimits and GlobalDynamicProperties
 * 5. trackAliases consumes 2 Addresses
 * 6. The class is stateless and the arguments are passed into the functions
 * 7. Use {@link EntityAddressSequencer} in place of EntityIdSource
 */
public abstract class AbstractAutoCreationLogic {

    private final FeeCalculator feeCalculator;
    private final EvmProperties evmProperties;
    private final SyntheticTxnFactory syntheticTxnFactory;

    private static final Supplier<Long> zeroLongSupplier = () -> 0L;
    private static final Supplier<Integer> zeroIntegerSupplier = () -> 0;

    protected AbstractAutoCreationLogic(
            final FeeCalculator feeCalculator,
            final EvmProperties evmProperties,
            final SyntheticTxnFactory syntheticTxnFactory) {
        this.feeCalculator = feeCalculator;
        this.evmProperties = evmProperties;
        this.syntheticTxnFactory = syntheticTxnFactory;
    }

    /**
     * Provisionally auto-creates an account in the given accounts ledger for the triggering balance change.
     *
     * <p>Returns the amount deducted from the balance change as an auto-creation charge; or a
     * failure code.
     *
     * <p><b>IMPORTANT:</b> If this change was to be part of a zero-sum balance change list, then
     * after those changes are applied atomically, the returned fee must be given to the funding account!
     *
     * @param change  a triggering change with unique alias
     * @param changes list of all changes need to construct tokenAliasMap
     * @return the fee charged for the auto-creation if ok, a failure reason otherwise
     */
    public Pair<ResponseCodeEnum, Long> create(
            final BalanceChange change,
            final Timestamp timestamp,
            final Store store,
            final EntityAddressSequencer ids,
            final List<BalanceChange> changes) {
        if (change.isForToken() && !evmProperties.isLazyCreationEnabled()) {
            return Pair.of(NOT_SUPPORTED, 0L);
        }
        final var alias = change.getNonEmptyAliasIfPresent();
        if (alias == null) {
            throw new IllegalStateException("Cannot auto-create an account from unaliased change " + change);
        }

        TransactionBody.Builder syntheticCreation;

        final var isAliasEVMAddress = alias.size() == EVM_ADDRESS_SIZE;
        final int maxAutoAssociations = getMaxAutoAssociations(alias, changes);
        if (isAliasEVMAddress) {
            syntheticCreation = syntheticTxnFactory.createHollowAccount(alias, 0L, maxAutoAssociations);
        } else {
            final var key = asPrimitiveKeyUnchecked(alias);
            syntheticCreation = syntheticTxnFactory.createAccount(alias, key, 0L, maxAutoAssociations);
        }

        var fee = autoCreationFeeFor(syntheticCreation, timestamp);
        if (isAliasEVMAddress) {
            fee += getLazyCreationFinalizationFee(timestamp);
        }

        final var newId = ids.getNewAccountId();
        final var account = new Account(
                alias,
                0L,
                Id.fromGrpcAccount(newId),
                0L,
                zeroLongSupplier,
                false,
                zeroLongSupplier,
                0L,
                null,
                maxAutoAssociations,
                Collections::emptySortedMap,
                Collections::emptySortedMap,
                Collections::emptySortedSet,
                zeroIntegerSupplier,
                zeroIntegerSupplier,
                0,
                0L,
                false,
                null,
                0L,
                0);
        store.updateAccount(account);

        replaceAliasAndSetBalanceOnChange(change, newId);
        trackAlias(alias, account.getAccountAddress());
        return Pair.of(OK, fee);
    }

    protected abstract void trackAlias(final ByteString alias, final Address address);

    private int getMaxAutoAssociations(final ByteString alias, final List<BalanceChange> changes) {
        var recordFile = ContractCallContext.get().getRecordFile();
        if (recordFile != null && recordFile.getHapiVersion().isGreaterThanOrEqualTo(RecordFile.HAPI_VERSION_0_53_0)) {
            return Account.UNLIMITED_AUTO_ASSOCIATIONS;
        }

        return getAutoAssociationCount(alias, changes);
    }

    private void replaceAliasAndSetBalanceOnChange(final BalanceChange change, final AccountID newAccountId) {
        if (change.isForHbar()) {
            change.setNewBalance(change.getAggregatedUnits());
        }
        change.replaceNonEmptyAliasWith(fromAccountId(newAccountId));
    }

    private long getLazyCreationFinalizationFee(Timestamp timestamp) {
        // an AccountID is already accounted for in the
        // fee estimator, so we just need to pass a stub ECDSA key
        // in the synthetic crypto update body
        final var updateTxnBody =
                CryptoUpdateTransactionBody.newBuilder().setKey(Key.newBuilder().setECDSASecp256K1(ByteString.EMPTY));
        return autoCreationFeeFor(TransactionBody.newBuilder().setCryptoUpdateAccount(updateTxnBody), timestamp);
    }

    private long autoCreationFeeFor(final TransactionBody.Builder cryptoCreateTxn, Timestamp timestamp) {
        final var accessor = synthAccessorFor(cryptoCreateTxn);
        final var fees = feeCalculator.computeFee(accessor, EMPTY_KEY, timestamp);
        return fees.getServiceFee() + fees.getNetworkFee() + fees.getNodeFee();
    }

    private int getAutoAssociationCount(final ByteString alias, final List<BalanceChange> changes) {
        final Set<Id> tokens = new HashSet<>();

        for (final var change : changes) {
            if (change.isForHbar()) {
                continue;
            }

            var entityAlias = change.getNonEmptyAliasIfPresent();
            if (Objects.equals(alias, entityAlias)) {
                tokens.add(change.getToken());
            }
        }

        return tokens.size();
    }
}
