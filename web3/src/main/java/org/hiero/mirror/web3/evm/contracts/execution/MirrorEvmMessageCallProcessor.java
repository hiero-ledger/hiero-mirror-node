// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution;

import static com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason.FAILURE_DURING_LAZY_ACCOUNT_CREATE;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.wrapUnsafely;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.EXCEPTIONAL_HALT;

import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.txns.crypto.AbstractAutoCreationLogic;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.hiero.mirror.web3.evm.config.PrecompiledContractProvider;
import org.hiero.mirror.web3.evm.store.contract.EntityAddressSequencer;
import org.hiero.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.MainnetPrecompiledContracts;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.tracing.OperationTracer;

public class MirrorEvmMessageCallProcessor extends AbstractEvmMessageCallProcessor {
    private final AbstractAutoCreationLogic autoCreationLogic;
    private final EntityAddressSequencer entityAddressSequencer;

    public MirrorEvmMessageCallProcessor(
            final AbstractAutoCreationLogic autoCreationLogic,
            final EntityAddressSequencer entityAddressSequencer,
            final EVM evm,
            final PrecompileContractRegistry precompiles,
            final PrecompiledContractProvider precompilesHolder,
            final GasCalculator gasCalculator,
            final Predicate<Address> systemAccountDetector) {
        super(evm, precompiles, precompilesHolder.getHederaPrecompiles(), systemAccountDetector);
        this.autoCreationLogic = autoCreationLogic;
        this.entityAddressSequencer = entityAddressSequencer;

        MainnetPrecompiledContracts.populateForIstanbul(precompiles, gasCalculator);
    }

    /**
     * This logic is copied from hedera-services HederaMessageCallProcessor.
     *
     * @param frame
     * @param operationTracer
     */
    @Override
    protected void executeLazyCreate(final MessageFrame frame, final OperationTracer operationTracer) {
        final var updater = (HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater();
        final var syntheticBalanceChange = constructSyntheticLazyCreateBalanceChangeFrom(frame);
        final var timestamp = Timestamp.newBuilder()
                .setSeconds(frame.getBlockValues().getTimestamp())
                .build();
        final var lazyCreateResult = autoCreationLogic.create(
                syntheticBalanceChange,
                timestamp,
                updater.getStore(),
                entityAddressSequencer,
                List.of(syntheticBalanceChange));
        if (lazyCreateResult.getLeft() != ResponseCodeEnum.OK) {
            haltFrameAndTraceCreationResult(frame, operationTracer, FAILURE_DURING_LAZY_ACCOUNT_CREATE);
        } else {
            final var creationFeeInTinybars = lazyCreateResult.getRight();
            final var creationFeeInGas =
                    creationFeeInTinybars / frame.getGasPrice().toLong();
            if (frame.getRemainingGas() < creationFeeInGas) {
                // ledgers won't be committed on unsuccessful frame and StackedContractAliases
                // will revert any new aliases
                haltFrameAndTraceCreationResult(frame, operationTracer, INSUFFICIENT_GAS);
            } else {
                frame.decrementRemainingGas(creationFeeInGas);

                // we do not track auto-creation preceding child record as the mirror node does not
                // maintain child record logic at the moment

                // track the lazy account so it is accessible to the EVM
                updater.trackLazilyCreatedAccount(EntityIdUtils.asTypedEvmAddress(syntheticBalanceChange.accountId()));
            }
        }
    }

    @NonNull
    private BalanceChange constructSyntheticLazyCreateBalanceChangeFrom(final MessageFrame frame) {
        return BalanceChange.changingHbar(
                AccountAmount.newBuilder()
                        .setAccountID(AccountID.newBuilder()
                                .setAlias(
                                        wrapUnsafely(frame.getRecipientAddress().toArrayUnsafe()))
                                .build())
                        .build(),
                null);
    }

    private void haltFrameAndTraceCreationResult(
            final MessageFrame frame, final OperationTracer operationTracer, final ExceptionalHaltReason haltReason) {
        frame.decrementRemainingGas(frame.getRemainingGas());
        frame.setState(EXCEPTIONAL_HALT);
        operationTracer.traceAccountCreationResult(frame, Optional.of(haltReason));
    }
}
