// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.hapi.node.base.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.collection;
import static org.hiero.mirror.web3.state.Utils.isMirror;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.app.workflows.standalone.TransactionExecutor;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.web3.ContextExtension;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.MirrorOperationActionTracer;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.MirrorOperationTracer;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeActionTracer;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.service.model.CallServiceParameters;
import org.hiero.mirror.web3.service.model.CallServiceParameters.CallType;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.state.keyvalue.AccountReadableKVState;
import org.hiero.mirror.web3.state.keyvalue.AliasesReadableKVState;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hiero.mirror.web3.web3j.generated.NestedCalls;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class})
@ExtendWith(ContextExtension.class)
class TransactionExecutionServiceTest {
    private static final Long DEFAULT_GAS = 50000L;

    @Mock
    private AccountReadableKVState accountReadableKVState;

    @Mock
    private AliasesReadableKVState aliasesReadableKVState;

    @Mock
    private MirrorOperationTracer mirrorOperationTracer;

    @Mock
    private OpcodeActionTracer opcodeActionTracer;

    @Mock
    private MirrorOperationActionTracer mirrorOperationActionTracer;

    @Mock
    private TransactionExecutor transactionExecutor;

    @Mock
    private TransactionExecutorFactory transactionExecutorFactory;

    private TransactionExecutionService transactionExecutionService;

    private static Stream<Arguments> provideCallData() {
        return Stream.of(
                Arguments.of(org.apache.tuweni.bytes.Bytes.EMPTY),
                Arguments.of(org.apache.tuweni.bytes.Bytes.fromHexString(NestedCalls.BINARY)));
    }

    @BeforeEach
    void setUp() {
        var commonProperties = new CommonProperties();
        var systemEntity = new SystemEntity(commonProperties);
        transactionExecutionService = new TransactionExecutionService(
                accountReadableKVState,
                aliasesReadableKVState,
                commonProperties,
                new MirrorNodeEvmProperties(commonProperties, systemEntity),
                opcodeActionTracer,
                mirrorOperationActionTracer,
                systemEntity,
                transactionExecutorFactory);
        when(transactionExecutorFactory.get()).thenReturn(transactionExecutor);
    }

    @ParameterizedTest
    @ValueSource(strings = "0x0000000000000000000000000000000000000000")
    void testExecuteContractCallSuccess(String senderAddressHex) {
        // Given
        ContractCallContext.get().setOpcodeTracerOptions(new OpcodeTracerOptions());

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
        // Mock the executor to return a List with the mocked SingleTransactionRecord
        when(transactionExecutor.execute(any(TransactionBody.class), any(Instant.class), any(OperationTracer[].class)))
                .thenReturn(List.of(singleTransactionRecord));

        var callServiceParameters = buildServiceParams(false, org.apache.tuweni.bytes.Bytes.EMPTY, senderAddress);

        // When
        var result = transactionExecutionService.execute(callServiceParameters, DEFAULT_GAS);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getGasUsed()).isEqualTo(DEFAULT_GAS);
        assertThat(result.getRevertReason()).isNotPresent();
    }

    @Nested
    class InvalidSenderNegativeTest {

        private static Stream<Arguments> invalidSenderAddress() {
            return Stream.of(
                    Arguments.of(Address.fromHexString("0x1234")),
                    Arguments.of(Address.fromHexString("0x627306090abab3a6e1400e9345bc60c78a8bef57")));
        }

        @BeforeEach
        void setup() {
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

            // Mock the executor to return a List with the mocked SingleTransactionRecord
            when(transactionExecutor.execute(
                            any(TransactionBody.class), any(Instant.class), any(OperationTracer[].class)))
                    .thenReturn(List.of(singleTransactionRecord));
        }

        @MockitoSettings(strictness = Strictness.LENIENT)
        @ParameterizedTest
        @MethodSource("invalidSenderAddress")
        void testExecuteContractCallInvalidSender(final Address senderAddress) {
            // Given
            if (isMirror(senderAddress)) {
                when(accountReadableKVState.get(any())).thenReturn(null);
            } else {
                when(aliasesReadableKVState.get(any())).thenReturn(null);
                when(accountReadableKVState.get(any())).thenReturn(mock(Account.class));
            }

            var callServiceParameters = buildServiceParams(false, org.apache.tuweni.bytes.Bytes.EMPTY, senderAddress);

            // Then
            assertThatThrownBy(() -> transactionExecutionService.execute(callServiceParameters, DEFAULT_GAS))
                    .isInstanceOf(MirrorEvmTransactionException.class)
                    .hasMessage(PAYER_ACCOUNT_NOT_FOUND.name());
        }

        @MockitoSettings(strictness = Strictness.LENIENT)
        @ParameterizedTest
        @MethodSource("invalidSenderAddress")
        void testExecuteContractCallInvalidSenderContract(final Address senderAddress) {
            // Given
            final var smartContractAccount = mock(Account.class);
            when(smartContractAccount.smartContract()).thenReturn(true);
            if (isMirror(senderAddress)) {
                when(accountReadableKVState.get(any())).thenReturn(smartContractAccount);
            } else {
                final var accountID = mock(AccountID.class);
                when(aliasesReadableKVState.get(any())).thenReturn(accountID);
                when(accountReadableKVState.get(accountID)).thenReturn(smartContractAccount);
            }

            var callServiceParameters = buildServiceParams(false, org.apache.tuweni.bytes.Bytes.EMPTY, senderAddress);

            // Then
            assertThatThrownBy(() -> transactionExecutionService.execute(callServiceParameters, DEFAULT_GAS))
                    .isInstanceOf(MirrorEvmTransactionException.class)
                    .hasMessage(PAYER_ACCOUNT_NOT_FOUND.name());
        }
    }

    @ParameterizedTest
    @CsvSource({
        "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000013536f6d6520726576657274206d65737361676500000000000000000000000000,CONTRACT_REVERT_EXECUTED,Some revert message, INVALID_NFT_ID",
        "INVALID_TOKEN_ID,CONTRACT_REVERT_EXECUTED,'', INVALID_TOKEN_NFT_SERIAL_NUMBER",
        "0x,INVALID_TOKEN_ID,'', TOKEN_REFERENCE_LIST_SIZE_LIMIT_EXCEEDED"
    })
    @SuppressWarnings("unused")
    void testExecuteContractCallFailureWithErrorMessageAndChildTransactionErrors(
            final String errorMessage,
            final ResponseCodeEnum responseCode,
            final String detail,
            final ResponseCodeEnum childResponseCode) {
        // Given
        // Mock the SingleTransactionRecord and TransactionRecord
        var singleTransactionRecord = mock(SingleTransactionRecord.class);
        var transactionRecord = mock(TransactionRecord.class);
        var transactionReceipt = mock(TransactionReceipt.class);

        var childSingleTransactionRecord = mock(SingleTransactionRecord.class);
        var childTransactionRecord = mock(TransactionRecord.class);
        var childTransactionReceipt = mock(TransactionReceipt.class);

        when(transactionReceipt.status()).thenReturn(responseCode);
        when(transactionRecord.receiptOrThrow()).thenReturn(transactionReceipt);
        when(singleTransactionRecord.transactionRecord()).thenReturn(transactionRecord);

        when(childTransactionReceipt.status()).thenReturn(childResponseCode);
        when(childTransactionRecord.receiptOrThrow()).thenReturn(childTransactionReceipt);
        when(childSingleTransactionRecord.transactionRecord()).thenReturn(childTransactionRecord);

        var contractFunctionResult = mock(ContractFunctionResult.class);
        when(transactionRecord.contractCallResult()).thenReturn(contractFunctionResult);
        when(contractFunctionResult.errorMessage()).thenReturn(errorMessage);

        // Mock the executor to return a List with the mocked SingleTransactionRecord
        when(transactionExecutor.execute(any(TransactionBody.class), any(Instant.class), any(OperationTracer[].class)))
                .thenReturn(List.of(singleTransactionRecord, childSingleTransactionRecord));

        var callServiceParameters = buildServiceParams(false, org.apache.tuweni.bytes.Bytes.EMPTY, Address.ZERO);

        // Then
        assertThatThrownBy(() -> transactionExecutionService.execute(callServiceParameters, DEFAULT_GAS))
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessageContaining(responseCode.name())
                .hasFieldOrPropertyWithValue("detail", detail)
                .hasFieldOrProperty("childTransactionErrors")
                .extracting("childTransactionErrors")
                .asInstanceOf(collection(String.class))
                .containsExactly(childResponseCode.protoName());
    }

    @ParameterizedTest
    @CsvSource({
        "0x,CONTRACT_REVERT_EXECUTED,'', SUCCESS",
        "0x,CONTRACT_REVERT_EXECUTED,'', REVERTED_SUCCESS",
        "0x,SUCCESS,'', REVERTED_SUCCESS"
    })
    @ExtendWith(OutputCaptureExtension.class)
    @SuppressWarnings("unused")
    void testExecuteContractCallWithChildTransactionErrors(
            final String errorMessage,
            final ResponseCodeEnum responseCode,
            final String detail,
            final ResponseCodeEnum childResponseCode,
            final CapturedOutput capturedOutput) {
        // Given
        // Mock the SingleTransactionRecord and TransactionRecord
        var singleTransactionRecord = mock(SingleTransactionRecord.class);
        var transactionRecord = mock(TransactionRecord.class);
        var transactionReceipt = mock(TransactionReceipt.class);

        var childSingleTransactionRecord = mock(SingleTransactionRecord.class);
        var childTransactionRecord = mock(TransactionRecord.class);
        var childTransactionReceipt = mock(TransactionReceipt.class);

        when(transactionReceipt.status()).thenReturn(responseCode);
        when(transactionRecord.receiptOrThrow()).thenReturn(transactionReceipt);
        when(singleTransactionRecord.transactionRecord()).thenReturn(transactionRecord);

        when(childTransactionReceipt.status()).thenReturn(childResponseCode);
        when(childTransactionRecord.receiptOrThrow()).thenReturn(childTransactionReceipt);
        when(childSingleTransactionRecord.transactionRecord()).thenReturn(childTransactionRecord);

        var contractFunctionResult = mock(ContractFunctionResult.class);

        // Mock the executor to return a List with the mocked SingleTransactionRecord
        when(transactionExecutor.execute(any(TransactionBody.class), any(Instant.class), any(OperationTracer[].class)))
                .thenReturn(List.of(singleTransactionRecord, childSingleTransactionRecord));

        var callServiceParameters = buildServiceParams(false, org.apache.tuweni.bytes.Bytes.EMPTY, Address.ZERO);

        // Then
        if (responseCode != SUCCESS) {
            when(contractFunctionResult.errorMessage()).thenReturn(errorMessage);
            when(transactionRecord.contractCallResult()).thenReturn(contractFunctionResult);

            assertThatThrownBy(() -> transactionExecutionService.execute(callServiceParameters, DEFAULT_GAS))
                    .isInstanceOf(MirrorEvmTransactionException.class)
                    .hasMessageContaining(responseCode.name())
                    .hasFieldOrPropertyWithValue("detail", detail)
                    .hasFieldOrProperty("childTransactionErrors")
                    .extracting("childTransactionErrors")
                    .asInstanceOf(collection(String.class))
                    .isEmpty();
        } else {
            when(contractFunctionResult.gasUsed()).thenReturn(DEFAULT_GAS);
            when(contractFunctionResult.contractCallResult()).thenReturn(Bytes.EMPTY);
            // Mock the transactionRecord to return the contract call result
            when(transactionRecord.contractCallResultOrThrow()).thenReturn(contractFunctionResult);

            // When
            var result = transactionExecutionService.execute(callServiceParameters, DEFAULT_GAS);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getGasUsed()).isEqualTo(DEFAULT_GAS);
            assertThat(result.getRevertReason()).isNotPresent();
        }
        // Validate no logs were produced
        assertThat(capturedOutput.getOut()).doesNotContain("childTransactionErrors");
    }

    @SuppressWarnings("unused")
    @Test
    void testExecuteContractCallFailureOnPreChecks() {
        // Given
        // Mock the SingleTransactionRecord and TransactionRecord
        var singleTransactionRecord = mock(SingleTransactionRecord.class);
        var transactionRecord = mock(TransactionRecord.class);
        var transactionReceipt = mock(TransactionReceipt.class);

        when(transactionRecord.receiptOrThrow()).thenReturn(transactionReceipt);
        when(transactionReceipt.status()).thenReturn(ResponseCodeEnum.INVALID_ACCOUNT_ID);
        when(singleTransactionRecord.transactionRecord()).thenReturn(transactionRecord);

        // Mock the executor to return a List with the mocked SingleTransactionRecord
        when(transactionExecutor.execute(any(TransactionBody.class), any(Instant.class), any(OperationTracer[].class)))
                .thenReturn(List.of(singleTransactionRecord));

        var callServiceParameters = buildServiceParams(false, org.apache.tuweni.bytes.Bytes.EMPTY, Address.ZERO);

        // Then
        assertThatThrownBy(() -> transactionExecutionService.execute(callServiceParameters, DEFAULT_GAS))
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessageContaining(ResponseCodeEnum.INVALID_ACCOUNT_ID.name());
    }

    // NestedCalls.BINARY
    @ParameterizedTest
    @MethodSource("provideCallData")
    void testExecuteContractCreateSuccess(org.apache.tuweni.bytes.Bytes callData) {
        // Given
        ContractCallContext.get().setOpcodeTracerOptions(new OpcodeTracerOptions());

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
        when(transactionExecutor.execute(any(TransactionBody.class), any(Instant.class), any(OperationTracer[].class)))
                .thenReturn(List.of(singleTransactionRecord));

        var callServiceParameters = buildServiceParams(true, callData, Address.ZERO);

        // When
        var result = transactionExecutionService.execute(callServiceParameters, DEFAULT_GAS);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getGasUsed()).isEqualTo(DEFAULT_GAS);
        assertThat(result.getRevertReason()).isNotPresent();
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
                .sender(senderAddress)
                .value(0)
                .build();
    }
}
