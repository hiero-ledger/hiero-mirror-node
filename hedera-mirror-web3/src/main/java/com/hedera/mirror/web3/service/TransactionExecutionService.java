/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.web3.convert.BytesDecoder.maybeDecodeSolidityErrorStringToReadableMessage;
import static com.hedera.mirror.web3.state.Utils.isMirror;
import static com.hedera.mirror.web3.validation.HexValidator.HEX_PREFIX;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.MirrorOperationTracer;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracer;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import com.hedera.mirror.web3.state.keyvalue.AliasesReadableKVState;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.config.data.EntitiesConfig;
import com.swirlds.state.State;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter.MeterProvider;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.tracing.OperationTracer;

@Named
@CustomLog
@RequiredArgsConstructor
public class TransactionExecutionService {

    private static final AccountID TREASURY_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(2).build();
    private static final Duration TRANSACTION_DURATION = new Duration(15);
    private static final long CONTRACT_CREATE_TX_FEE = 100_000_000L;

    private final State mirrorNodeState;
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;
    private final OpcodeTracer opcodeTracer;
    private final MirrorOperationTracer mirrorOperationTracer;
    private final TransactionExecutorFactory transactionExecutorFactory;

    public HederaEvmTransactionProcessingResult execute(
            final CallServiceParameters params, final long estimatedGas, final MeterProvider<Counter> gasUsedCounter) {
        final var isContractCreate = params.getReceiver().isZero();
        final var configuration = mirrorNodeEvmProperties.getVersionedConfiguration();
        final var maxLifetime =
                configuration.getConfigData(EntitiesConfig.class).maxLifetime();
        var executor = transactionExecutorFactory.get();

        TransactionBody transactionBody;
        HederaEvmTransactionProcessingResult result = null;
        if (isContractCreate) {
            transactionBody = buildContractCreateTransactionBody(params, estimatedGas, maxLifetime);
        } else {
            transactionBody = buildContractCallTransactionBody(params, estimatedGas);
        }

        var receipt = executor.execute(transactionBody, Instant.now(), getOperationTracers());
        var transactionRecord = receipt.getFirst().transactionRecord();
        if (transactionRecord.receiptOrThrow().status() == com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS) {
            result = buildSuccessResult(isContractCreate, transactionRecord, params);
        } else {
            result = handleFailedResult(transactionRecord, isContractCreate, gasUsedCounter);
        }
        return result;
    }

    // Duplicated code as in ContractCallService class - it will be removed from there when switch to the modularized
    // implementation entirely.
    private void updateErrorGasUsedMetric(
            final MeterProvider<Counter> gasUsedCounter, final long gasUsed, final int iterations) {
        gasUsedCounter
                .withTags("type", CallType.ERROR.toString(), "iteration", String.valueOf(iterations))
                .increment(gasUsed);
    }

    private ContractFunctionResult getTransactionResult(
            final TransactionRecord transactionRecord, boolean isContractCreate) {
        return isContractCreate
                ? transactionRecord.contractCreateResultOrThrow()
                : transactionRecord.contractCallResultOrThrow();
    }

    private HederaEvmTransactionProcessingResult buildSuccessResult(
            final boolean isContractCreate,
            final TransactionRecord transactionRecord,
            final CallServiceParameters params) {
        var result = getTransactionResult(transactionRecord, isContractCreate);

        return HederaEvmTransactionProcessingResult.successful(
                List.of(),
                result.gasUsed(),
                0L,
                0L,
                Bytes.wrap(result.contractCallResult().toByteArray()),
                params.getReceiver());
    }

    private HederaEvmTransactionProcessingResult handleFailedResult(
            final TransactionRecord transactionRecord,
            final boolean isContractCreate,
            final MeterProvider<Counter> gasUsedCounter)
            throws MirrorEvmTransactionException {
        var result =
                isContractCreate ? transactionRecord.contractCreateResult() : transactionRecord.contractCallResult();
        var status = transactionRecord.receiptOrThrow().status();
        if (result == null) {
            // No result - the call did not reach the EVM and probably failed at pre-checks. No metric to update in this
            // case.
            throw new MirrorEvmTransactionException(status.protoName(), StringUtils.EMPTY, StringUtils.EMPTY);
        } else {
            var errorMessage = getErrorMessage(result).orElse(Bytes.EMPTY);
            var detail = maybeDecodeSolidityErrorStringToReadableMessage(errorMessage);
            updateErrorGasUsedMetric(gasUsedCounter, result.gasUsed(), 1);
            if (ContractCallContext.get().getOpcodeTracerOptions() == null) {
                throw new MirrorEvmTransactionException(
                        status.protoName(),
                        detail,
                        errorMessage.toHexString(),
                        HederaEvmTransactionProcessingResult.failed(
                                result.gasUsed(), 0L, 0L, Optional.of(errorMessage), Optional.empty()));
            } else {
                // If we are in an opcode trace scenario, we need to return a failed result in order to get the
                // opcode list from the ContractCallContext. If we throw an exception instead of returning a result,
                // as in the regular case, we won't be able to get the opcode list.
                return HederaEvmTransactionProcessingResult.failed(
                        result.gasUsed(), 0L, 0L, Optional.of(errorMessage), Optional.empty());
            }
        }
    }

    private Optional<Bytes> getErrorMessage(final ContractFunctionResult result) {
        return result.errorMessage().startsWith(HEX_PREFIX)
                ? Optional.of(Bytes.fromHexString(result.errorMessage()))
                : Optional.empty(); // If it doesn't start with 0x, the message is already decoded and readable.
    }

    private TransactionBody.Builder defaultTransactionBodyBuilder(final CallServiceParameters params) {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder()
                        .transactionValidStart(new Timestamp(Instant.now().getEpochSecond(), 0))
                        .accountID(getSenderAccountID(params))
                        .build())
                .nodeAccountID(TREASURY_ACCOUNT_ID) // We don't really need another account here.
                .transactionValidDuration(TRANSACTION_DURATION);
    }

    private TransactionBody buildContractCreateTransactionBody(
            final CallServiceParameters params, long estimatedGas, long maxLifetime) {
        return defaultTransactionBodyBuilder(params)
                .contractCreateInstance(ContractCreateTransactionBody.newBuilder()
                        .initcode(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(
                                params.getCallData().toArrayUnsafe()))
                        .gas(estimatedGas)
                        .autoRenewPeriod(new Duration(maxLifetime))
                        .build())
                .transactionFee(CONTRACT_CREATE_TX_FEE)
                .build();
    }

    private TransactionBody buildContractCallTransactionBody(
            final CallServiceParameters params, final long estimatedGas) {
        return defaultTransactionBodyBuilder(params)
                .contractCall(ContractCallTransactionBody.newBuilder()
                        .contractID(ContractID.newBuilder()
                                .evmAddress(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(
                                        params.getReceiver().toArrayUnsafe()))
                                .build())
                        .functionParameters(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(
                                params.getCallData().toArrayUnsafe()))
                        .amount(params.getValue()) // tinybars sent to contract
                        .gas(estimatedGas)
                        .build())
                .build();
    }

    private ProtoBytes convertAddressToProtoBytes(final Address address) {
        return ProtoBytes.newBuilder()
                .value(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(address.toArrayUnsafe()))
                .build();
    }

    private AccountID getSenderAccountID(final CallServiceParameters params) {
        if (params.getSender().canonicalAddress().isZero() && params.getValue() == 0L) {
            // Set a default account to keep the sender parameter optional.
            return TREASURY_ACCOUNT_ID;
        }

        final var senderAddress = params.getSender().canonicalAddress();
        if (isMirror(senderAddress)) {
            return AccountID.newBuilder()
                    .accountNum(senderAddress.trimLeadingZeros().toLong())
                    .build();
        }
        return (AccountID) mirrorNodeState
                .getReadableStates(TokenService.NAME)
                .get(AliasesReadableKVState.KEY)
                .get(convertAddressToProtoBytes(senderAddress));
    }

    private OperationTracer[] getOperationTracers() {
        return ContractCallContext.get().getOpcodeTracerOptions() != null
                ? new OperationTracer[] {opcodeTracer}
                : new OperationTracer[] {mirrorOperationTracer};
    }
}
