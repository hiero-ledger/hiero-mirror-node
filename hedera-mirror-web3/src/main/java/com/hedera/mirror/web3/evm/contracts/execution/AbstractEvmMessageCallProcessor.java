// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.evm.contracts.execution;

import static com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract.EVM_HTS_PRECOMPILED_CONTRACT_ADDRESS;
import static com.hedera.services.stream.proto.ContractActionType.PRECOMPILE;
import static com.hedera.services.stream.proto.ContractActionType.SYSTEM;
import static org.apache.tuweni.bytes.Bytes.EMPTY;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.DefaultExceptionalHaltReason.INSUFFICIENT_GAS;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.COMPLETED_SUCCESS;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.EXCEPTIONAL_HALT;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.REVERT;

import com.hedera.node.app.service.evm.contracts.execution.HederaEvmMessageCallProcessor;
import com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason;
import com.hedera.node.app.service.evm.store.contracts.AbstractLedgerEvmWorldUpdater;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.mono.contracts.execution.traceability.HederaOperationTracer;
import com.swirlds.base.utility.Pair;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.tracing.OperationTracer;

public abstract class AbstractEvmMessageCallProcessor extends HederaEvmMessageCallProcessor {

    private final Predicate<Address> isNativePrecompileCheck;

    private final Predicate<Address> systemAccountDetector;

    protected AbstractEvmMessageCallProcessor(
            final EVM evm,
            final PrecompileContractRegistry precompiles,
            final Map<String, PrecompiledContract> hederaPrecompileList,
            final Predicate<Address> systemAccountDetector) {
        super(evm, precompiles, hederaPrecompileList);
        isNativePrecompileCheck = addr -> precompiles.get(addr) != null;
        this.systemAccountDetector = systemAccountDetector;
    }

    @Override
    public void start(final MessageFrame frame, final OperationTracer operationTracer) {
        super.start(frame, operationTracer);

        // potential precompile execution will be done after super.start(), so trace results here
        final Address contractAddress = frame.getContractAddress();
        final boolean isNativePrecompile = isNativePrecompileCheck.test(contractAddress);
        final boolean isHederaPrecompile = hederaPrecompiles.containsKey(contractAddress);
        if ((isNativePrecompile || isHederaPrecompile)
                && operationTracer instanceof HederaOperationTracer hederaOperationTracer) {
            hederaOperationTracer.tracePrecompileResult(frame, isHederaPrecompile ? SYSTEM : PRECOMPILE);
        }
        final Integer evmHtsPrecompiledContractAddressNum =
                Integer.parseInt(EVM_HTS_PRECOMPILED_CONTRACT_ADDRESS.substring(2), 16);
        // check a contract address because an HTS token create call can receive value
        final boolean nonHTS =
                Integer.compareUnsigned(contractAddress.getInt(16), evmHtsPrecompiledContractAddressNum) != 0;

        if (!isNativePrecompile
                && systemAccountDetector.test(frame.getContractAddress())
                && frame.getValue().greaterThan(Wei.ZERO)
                && nonHTS) {
            // cannot send value to native precompile calls, since there are collisions with system account
            // and value will be transferred to the system account, which is undesired
            frame.setExceptionalHaltReason(Optional.of(HederaExceptionalHaltReason.INVALID_FEE_SUBMITTED));
            frame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
            operationTracer.tracePostExecution(
                    frame,
                    new Operation.OperationResult(
                            frame.getRemainingGas(), HederaExceptionalHaltReason.INVALID_FEE_SUBMITTED));
        }
    }

    @Override
    protected void executeHederaPrecompile(
            PrecompiledContract contract, MessageFrame frame, OperationTracer operationTracer) {
        Pair<Long, Bytes> costAndResult = calculatePrecompileGasAndOutput(contract, frame);

        Long gasRequirement = costAndResult.left();
        Bytes output = costAndResult.right();

        traceAndHandleExecutionResult(frame, operationTracer, gasRequirement, output);
    }

    private Pair<Long, Bytes> calculatePrecompileGasAndOutput(PrecompiledContract contract, MessageFrame frame) {
        Bytes output = EMPTY;
        Long gasRequirement = 0L;

        if (contract instanceof EvmHTSPrecompiledContract htsPrecompile) {
            var updater = (AbstractLedgerEvmWorldUpdater) frame.getWorldUpdater();
            final var costedResult = htsPrecompile.computeCosted(
                    frame.getInputData(),
                    frame,
                    (now, minimumTinybarCost) -> minimumTinybarCost,
                    updater.tokenAccessor());
            output = costedResult.getValue();
            gasRequirement = costedResult.getKey();
        }

        if (!"HTS".equals(contract.getName()) && !"EvmHTS".equals(contract.getName())) {
            output = contract.computePrecompile(frame.getInputData(), frame).getOutput();
            gasRequirement = contract.gasRequirement(frame.getInputData());
        }

        return Pair.of(gasRequirement, output);
    }

    private void traceAndHandleExecutionResult(
            MessageFrame frame, OperationTracer operationTracer, Long gasRequirement, Bytes output) {
        operationTracer.tracePrecompileCall(frame, gasRequirement, output);
        if (frame.getState() == REVERT) {
            return;
        }

        if (frame.getRemainingGas() < gasRequirement) {
            frame.decrementRemainingGas(frame.getRemainingGas());
            frame.setExceptionalHaltReason(Optional.of(INSUFFICIENT_GAS));
            frame.setState(EXCEPTIONAL_HALT);
        } else if (output != null) {
            frame.decrementRemainingGas(gasRequirement);
            frame.setOutputData(output);
            frame.setState(COMPLETED_SUCCESS);
        } else {
            frame.setState(EXCEPTIONAL_HALT);
        }
    }
}
