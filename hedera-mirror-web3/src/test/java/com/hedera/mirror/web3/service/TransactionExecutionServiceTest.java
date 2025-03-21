// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.web3.state.Utils.isMirror;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.MirrorOperationTracer;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracer;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import com.hedera.mirror.web3.service.model.ContractExecutionParameters;
import com.hedera.mirror.web3.state.keyvalue.AliasesReadableKVState;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.web3j.generated.NestedCalls;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.app.workflows.standalone.TransactionExecutor;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.State;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableStates;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter.MeterProvider;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionExecutionServiceTest {
    private static final Long DEFAULT_GAS = 50000L;

    @Mock
    private State mirrorNodeState;

    @Mock
    private OpcodeTracer opcodeTracer;

    @Mock
    private MirrorOperationTracer mirrorOperationTracer;

    @Mock
    private TransactionExecutor transactionExecutor;

    @Mock
    private TransactionExecutorFactory transactionExecutorFactory;

    @Mock
    private MeterProvider<Counter> gasUsedCounter;

    private TransactionExecutionService transactionExecutionService;

    private static Stream<Arguments> provideCallData() {
        return Stream.of(
                Arguments.of(org.apache.tuweni.bytes.Bytes.EMPTY),
                Arguments.of(org.apache.tuweni.bytes.Bytes.fromHexString(NestedCalls.BINARY)));
    }

    @BeforeEach
    void setUp() {
        transactionExecutionService = new TransactionExecutionService(
                mirrorNodeState,
                new MirrorNodeEvmProperties(new CommonProperties()),
                opcodeTracer,
                mirrorOperationTracer,
                transactionExecutorFactory);
        when(transactionExecutorFactory.get()).thenReturn(transactionExecutor);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "0x0000000000000000000000000000000000000000",
                "0x1234",
                "0x627306090abab3a6e1400e9345bc60c78a8bef57"
            })
    void testExecuteContractCallSuccess(String senderAddressHex) {
        // Given
        ContractCallContext.run(context -> {
            context.setOpcodeTracerOptions(new OpcodeTracerOptions());

            // Mock the SingleTransactionRecord and TransactionRecord
            var singleTransactionRecord = mock(SingleTransactionRecord.class);
            var transactionRecord = mock(TransactionRecord.class);
            var transactionReceipt = mock(TransactionReceipt.class);

            // Simulate SUCCESS status in the receipt
            when(transactionReceipt.status()).thenReturn(ResponseCodeEnum.SUCCESS);
            when(transactionRecord.receiptOrThrow()).thenReturn(transactionReceipt);
            when(singleTransactionRecord.transactionRecord()).thenReturn(transactionRecord);

            var contractFunctionResult = mock(ContractFunctionResult.class);
            when(contractFunctionResult.gasUsed()).thenReturn(DEFAULT_GAS);
            when(contractFunctionResult.contractCallResult()).thenReturn(Bytes.EMPTY);

            // Mock the transactionRecord to return the contract call result
            when(transactionRecord.contractCallResultOrThrow()).thenReturn(contractFunctionResult);

            final var senderAddress = Address.fromHexString(senderAddressHex);
            if (!isMirror(senderAddress)) {
                final var readableStates = mock(ReadableStates.class);
                when(mirrorNodeState.getReadableStates(any())).thenReturn(readableStates);

                final var aliasesReadableKVState = mock(ReadableKVState.class);
                when(readableStates.get(AliasesReadableKVState.KEY)).thenReturn(aliasesReadableKVState);
            }

            // Mock the executor to return a List with the mocked SingleTransactionRecord
            when(transactionExecutor.execute(
                            any(TransactionBody.class), any(Instant.class), any(OperationTracer[].class)))
                    .thenReturn(List.of(singleTransactionRecord));

            var callServiceParameters = buildServiceParams(false, org.apache.tuweni.bytes.Bytes.EMPTY, senderAddress);

            // When
            var result = transactionExecutionService.execute(callServiceParameters, DEFAULT_GAS, gasUsedCounter);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getGasUsed()).isEqualTo(DEFAULT_GAS);
            assertThat(result.getRevertReason()).isNotPresent();
            return null;
        });
    }

    @ParameterizedTest
    @CsvSource({
        "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000013536f6d6520726576657274206d65737361676500000000000000000000000000,CONTRACT_REVERT_EXECUTED,Some revert message",
        "INVALID_TOKEN_ID,CONTRACT_REVERT_EXECUTED,''",
        "0x,INVALID_TOKEN_ID,''"
    })
    @SuppressWarnings("unused")
    void testExecuteContractCallFailureWithErrorMessage(
            final String errorMessage, final ResponseCodeEnum responseCode, final String detail) {
        // Given
        ContractCallContext.run(context -> {
            // Mock the SingleTransactionRecord and TransactionRecord
            var singleTransactionRecord = mock(SingleTransactionRecord.class);
            var transactionRecord = mock(TransactionRecord.class);
            var transactionReceipt = mock(TransactionReceipt.class);

            when(transactionReceipt.status()).thenReturn(responseCode);
            when(transactionRecord.receiptOrThrow()).thenReturn(transactionReceipt);
            when(singleTransactionRecord.transactionRecord()).thenReturn(transactionRecord);

            var contractFunctionResult = mock(ContractFunctionResult.class);
            when(transactionRecord.contractCallResult()).thenReturn(contractFunctionResult);
            when(contractFunctionResult.errorMessage()).thenReturn(errorMessage);
            when(gasUsedCounter.withTags(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mock(Counter.class));

            // Mock the executor to return a List with the mocked SingleTransactionRecord
            when(transactionExecutor.execute(
                            any(TransactionBody.class), any(Instant.class), any(OperationTracer[].class)))
                    .thenReturn(List.of(singleTransactionRecord));

            var callServiceParameters = buildServiceParams(false, org.apache.tuweni.bytes.Bytes.EMPTY, Address.ZERO);

            // Then
            assertThatThrownBy(() ->
                            transactionExecutionService.execute(callServiceParameters, DEFAULT_GAS, gasUsedCounter))
                    .isInstanceOf(MirrorEvmTransactionException.class)
                    .hasMessageContaining(responseCode.name())
                    .hasFieldOrPropertyWithValue("detail", detail);
            return null;
        });
    }

    @SuppressWarnings("unused")
    @Test
    void testExecuteContractCallFailureOnPreChecks() {
        // Given
        ContractCallContext.run(context -> {
            // Mock the SingleTransactionRecord and TransactionRecord
            var singleTransactionRecord = mock(SingleTransactionRecord.class);
            var transactionRecord = mock(TransactionRecord.class);
            var transactionReceipt = mock(TransactionReceipt.class);

            when(transactionRecord.receiptOrThrow()).thenReturn(transactionReceipt);
            when(transactionReceipt.status()).thenReturn(ResponseCodeEnum.INVALID_ACCOUNT_ID);
            when(singleTransactionRecord.transactionRecord()).thenReturn(transactionRecord);

            // Mock the executor to return a List with the mocked SingleTransactionRecord
            when(transactionExecutor.execute(
                            any(TransactionBody.class), any(Instant.class), any(OperationTracer[].class)))
                    .thenReturn(List.of(singleTransactionRecord));

            var callServiceParameters = buildServiceParams(false, org.apache.tuweni.bytes.Bytes.EMPTY, Address.ZERO);

            // Then
            assertThatThrownBy(() ->
                            transactionExecutionService.execute(callServiceParameters, DEFAULT_GAS, gasUsedCounter))
                    .isInstanceOf(MirrorEvmTransactionException.class)
                    .hasMessageContaining(ResponseCodeEnum.INVALID_ACCOUNT_ID.name());
            return null;
        });
    }

    // NestedCalls.BINARY
    @ParameterizedTest
    @MethodSource("provideCallData")
    void testExecuteContractCreateSuccess(org.apache.tuweni.bytes.Bytes callData) {
        // Given
        ContractCallContext.run(context -> {
            context.setOpcodeTracerOptions(new OpcodeTracerOptions());

            // Mock the SingleTransactionRecord and TransactionRecord
            var singleTransactionRecord = mock(SingleTransactionRecord.class);
            var transactionRecord = mock(TransactionRecord.class);
            var transactionReceipt = mock(TransactionReceipt.class);

            when(transactionReceipt.status()).thenReturn(ResponseCodeEnum.SUCCESS);
            when(transactionRecord.receiptOrThrow()).thenReturn(transactionReceipt);
            when(singleTransactionRecord.transactionRecord()).thenReturn(transactionRecord);

            var contractFunctionResult = mock(ContractFunctionResult.class);
            when(contractFunctionResult.gasUsed()).thenReturn(DEFAULT_GAS);
            when(contractFunctionResult.contractCallResult()).thenReturn(Bytes.EMPTY);

            // Mock the transactionRecord to return the contract call result
            when(transactionRecord.contractCreateResultOrThrow()).thenReturn(contractFunctionResult);

            // Mock the executor to return a List with the mocked SingleTransactionRecord
            when(transactionExecutor.execute(
                            any(TransactionBody.class), any(Instant.class), any(OperationTracer[].class)))
                    .thenReturn(List.of(singleTransactionRecord));

            var callServiceParameters = buildServiceParams(true, callData, Address.ZERO);

            // When
            var result = transactionExecutionService.execute(callServiceParameters, DEFAULT_GAS, gasUsedCounter);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getGasUsed()).isEqualTo(DEFAULT_GAS);
            assertThat(result.getRevertReason()).isNotPresent();
            return null;
        });
    }

    private CallServiceParameters buildServiceParams(
            boolean isContractCreate, org.apache.tuweni.bytes.Bytes callData, final Address senderAddress) {
        return ContractExecutionParameters.builder()
                .block(BlockType.LATEST)
                .callData(callData)
                .callType(CallType.ETH_CALL)
                .gas(DEFAULT_GAS)
                .isEstimate(false)
                .isStatic(true)
                .receiver(isContractCreate ? Address.ZERO : Address.fromHexString("0x1234"))
                .sender(new HederaEvmAccount(senderAddress))
                .value(0)
                .build();
    }
}
