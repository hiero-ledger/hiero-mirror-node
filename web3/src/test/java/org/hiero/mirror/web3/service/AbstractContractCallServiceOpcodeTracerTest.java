// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.EVM_ADDRESS_LENGTH;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.hiero.mirror.web3.utils.Constants.OPCODES_URI;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.ESTIMATE_GAS_ERROR_MESSAGE;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.TRANSACTION_GAS_LIMIT;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.isWithinExpectedGasRange;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.longValueOf;
import static org.hiero.mirror.web3.utils.OpcodeTracerUtil.OPTIONS;
import static org.hiero.mirror.web3.utils.OpcodeTracerUtil.gasComparator;
import static org.hiero.mirror.web3.utils.OpcodeTracerUtil.toHumanReadableMessage;
import static org.mockito.Mockito.doAnswer;

import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.tableusage.EndpointContext;
import org.hiero.mirror.rest.model.OpcodesResponse;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.convert.BytesDecoder;
import org.hiero.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.Opcode;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import org.hiero.mirror.web3.evm.store.accessor.EntityDatabaseAccessor;
import org.hiero.mirror.web3.repository.EntityRepository;
import org.hiero.mirror.web3.service.model.ContractDebugParameters;
import org.hiero.mirror.web3.utils.ContractFunctionProviderRecord;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.tx.Contract;

abstract class AbstractContractCallServiceOpcodeTracerTest extends AbstractContractCallServiceHistoricalTest {

    private static final String CALL_OPCODE_NAME = "CALL";

    @Resource
    protected ContractDebugService contractDebugService;

    @MockitoSpyBean
    protected TransactionExecutionService transactionExecutionService;

    @Resource
    protected EntityRepository entityRepository;

    @Captor
    private ArgumentCaptor<ContractDebugParameters> paramsCaptor;

    @Captor
    private ArgumentCaptor<Long> gasCaptor;

    private HederaEvmTransactionProcessingResult resultCaptor;
    private ContractCallContext contextCaptor;

    @Resource
    private EntityDatabaseAccessor entityDatabaseAccessor;

    protected static void setOpcodeEndpoint() {
        EndpointContext.setCurrentEndpoint(OPCODES_URI);
    }

    @BeforeEach
    void setUpArgumentCaptors() {
        if (!mirrorNodeEvmProperties.isModularizedServices()) {
            mirrorNodeEvmProperties.setModularizedTrafficPercent(0.0);
            doAnswer(invocation -> {
                        final var transactionProcessingResult =
                                (HederaEvmTransactionProcessingResult) invocation.callRealMethod();
                        resultCaptor = transactionProcessingResult;
                        contextCaptor = ContractCallContext.get();
                        return transactionProcessingResult;
                    })
                    .when(processor)
                    .execute(paramsCaptor.capture(), gasCaptor.capture());
        } else {
            mirrorNodeEvmProperties.setModularizedTrafficPercent(1.0);
            doAnswer(invocation -> {
                        final var transactionProcessingResult =
                                (HederaEvmTransactionProcessingResult) invocation.callRealMethod();
                        resultCaptor = transactionProcessingResult;
                        contextCaptor = ContractCallContext.get();
                        return transactionProcessingResult;
                    })
                    .when(transactionExecutionService)
                    .execute(paramsCaptor.capture(), gasCaptor.capture());
        }
    }

    protected void verifyOpcodeTracerCall(
            final String callData, final ContractFunctionProviderRecord functionProvider) {
        setOpcodeEndpoint();
        final var callDataBytes = Bytes.fromHexString(callData);
        final var debugParameters = getDebugParameters(functionProvider, callDataBytes);

        if (functionProvider.expectedErrorMessage() != null) {
            verifyThrowingOpcodeTracerCall(debugParameters, functionProvider);
        } else {
            verifySuccessfulOpcodeTracerCall(debugParameters);
        }
        assertThat(paramsCaptor.getValue()).isEqualTo(debugParameters);
        assertThat(gasCaptor.getValue()).isEqualTo(debugParameters.getGas());
    }

    protected void verifyOpcodeTracerCall(final String callData, final Contract contract) {
        setOpcodeEndpoint();
        ContractFunctionProviderRecord functionProvider = ContractFunctionProviderRecord.builder()
                .contractAddress(Address.fromHexString(contract.getContractAddress()))
                .build();

        final var callDataBytes = Bytes.fromHexString(callData);
        final var debugParameters = getDebugParameters(functionProvider, callDataBytes);

        if (functionProvider.expectedErrorMessage() != null) {
            verifyThrowingOpcodeTracerCall(debugParameters, functionProvider);
        } else {
            verifySuccessfulOpcodeTracerCall(debugParameters);
        }
        assertThat(paramsCaptor.getValue()).isEqualTo(debugParameters);
        assertThat(gasCaptor.getValue()).isEqualTo(debugParameters.getGas());
    }

    @SneakyThrows
    protected void verifyThrowingOpcodeTracerCall(
            final ContractDebugParameters params, final ContractFunctionProviderRecord function) {
        final var actual = ContractCallContext.run(ctx -> contractDebugService.processOpcodeCall(params, OPTIONS, ctx));
        assertThat(actual.transactionProcessingResult().isSuccessful()).isFalse();
        assertThat(actual.transactionProcessingResult().getOutput()).isEqualTo(Bytes.EMPTY);
        assertThat(actual.transactionProcessingResult())
                .satisfiesAnyOf(
                        result -> assertThat(result.getRevertReason())
                                .isPresent()
                                .map(BytesDecoder::maybeDecodeSolidityErrorStringToReadableMessage)
                                .hasValue(function.expectedErrorMessage()),
                        result -> assertThat(result.getHaltReason())
                                .isPresent()
                                .map(ExceptionalHaltReason::getDescription)
                                .hasValue(function.expectedErrorMessage()));
        assertThat(actual.opcodes().size()).isNotZero();
        assertThat(toHumanReadableMessage(actual.opcodes().getLast().reason()))
                .isEqualTo(function.expectedErrorMessage());
    }

    protected void verifySuccessfulOpcodeTracerCall(final ContractDebugParameters params) {
        final var actual = ContractCallContext.run(ctx -> contractDebugService.processOpcodeCall(params, OPTIONS, ctx));
        final var expected = new OpcodesProcessingResult(resultCaptor, contextCaptor.getOpcodes());
        // Compare transaction processing result
        assertThat(actual.transactionProcessingResult())
                .usingRecursiveComparison()
                .ignoringFields("logs")
                .isEqualTo(expected.transactionProcessingResult());
        // Compare opcodes with gas tolerance
        assertThat(actual.opcodes())
                .usingRecursiveComparison()
                .withComparatorForFields(gasComparator(), "gas")
                .isEqualTo(expected.opcodes());
    }

    protected void verifyOpcodesResponse(final OpcodesResponse opcodesResponse, final OpcodeTracerOptions options) {
        assertThat(opcodesResponse).isEqualTo(expectedOpcodesResponse(resultCaptor, contextCaptor.getOpcodes()));
        assertThat(gasCaptor.getValue()).isEqualTo(TRANSACTION_GAS_LIMIT);
        assertThat(contextCaptor.getOpcodeTracerOptions()).isEqualTo(options);
    }

    protected void verifyOpcodesResponseWithExpectedReturnValue(
            final OpcodesResponse opcodesResponse,
            final OpcodeTracerOptions options,
            final String expectedReturnValue) {
        assertThat(opcodesResponse).isEqualTo(expectedOpcodesResponse(resultCaptor, contextCaptor.getOpcodes()));
        assertThat(gasCaptor.getValue()).isEqualTo(TRANSACTION_GAS_LIMIT);
        assertThat(contextCaptor.getOpcodeTracerOptions()).isEqualTo(options);

        assertThat(opcodesResponse.getFailed()).isFalse();
        assertThat(opcodesResponse.getReturnValue()).isEqualTo(expectedReturnValue);
        // This assures that nested transactions are also being tracked
        assertThat(opcodesResponse.getOpcodes().stream()
                        .filter(o -> o.getOp().equals(CALL_OPCODE_NAME))
                        .count())
                .isGreaterThan(0);
    }

    private OpcodesResponse expectedOpcodesResponse(
            final HederaEvmTransactionProcessingResult result, final List<Opcode> opcodes) {
        return new OpcodesResponse()
                .address(result.getRecipient()
                        .flatMap(address -> entityDatabaseAccessor.get(address, Optional.empty()))
                        .map(this::entityAddress)
                        .map(Address::toHexString)
                        .orElse(Address.ZERO.toHexString()))
                .contractId(result.getRecipient()
                        .flatMap(address -> entityDatabaseAccessor.get(address, Optional.empty()))
                        .map(Entity::toEntityId)
                        .map(EntityId::toString)
                        .orElse(null))
                .failed(!result.isSuccessful())
                .gas(result.getGasUsed())
                .opcodes(opcodes.stream()
                        .map(opcode -> new org.hiero.mirror.rest.model.Opcode()
                                .depth(opcode.depth())
                                .gas(opcode.gas())
                                .gasCost(opcode.gasCost())
                                .op(opcode.op())
                                .pc(opcode.pc())
                                .reason(opcode.reason())
                                .stack(opcode.stack().stream()
                                        .map(Bytes::toHexString)
                                        .toList())
                                .memory(opcode.memory().stream()
                                        .map(Bytes::toHexString)
                                        .toList())
                                .storage(opcode.storage().entrySet().stream()
                                        .collect(Collectors.toMap(
                                                entry -> entry.getKey().toHexString(),
                                                entry -> entry.getValue().toHexString()))))
                        .toList())
                .returnValue(Optional.ofNullable(result.getOutput())
                        .map(Bytes::toHexString)
                        .orElse(Bytes.EMPTY.toHexString()));
    }

    private Address entityAddress(Entity entity) {
        if (entity == null) {
            return Address.ZERO;
        }
        if (entity.getEvmAddress() != null && entity.getEvmAddress().length == EVM_ADDRESS_LENGTH) {
            return Address.wrap(Bytes.wrap(entity.getEvmAddress()));
        }
        if (entity.getAlias() != null && entity.getAlias().length == EVM_ADDRESS_LENGTH) {
            return Address.wrap(Bytes.wrap(entity.getAlias()));
        }
        return toAddress(entity.toEntityId());
    }

    protected void accountBalanceRecordsPersist(Entity sender) {
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(sender.getCreatedTimestamp(), sender.toEntityId()))
                        .balance(sender.getBalance()))
                .persist();

        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(sender.getCreatedTimestamp(), treasuryEntity.toEntityId()))
                        .balance(treasuryEntity.getBalance()))
                .persist();
    }

    /**
     * Persists a record in the account_balance table (consensus_timestamp, balance, account_id). Each record represents
     * the HBAR balance of an account at a particular point in time(consensus timestamp). Lack of sufficient account
     * balance will result in INSUFFICIENT_PAYER_BALANCE exception, when trying to pay for transaction execution.
     *
     * @param accountId the account's id whose balance is being recorded
     * @param timestamp the point in time at which the account had the given balance
     * @param balance   the account's balance at the given timestamp
     */
    protected void accountBalanceRecordsPersist(EntityId accountId, Long timestamp, Long balance) {
        domainBuilder
                .accountBalance()
                .customize(
                        ab -> ab.id(new AccountBalance.Id(timestamp, accountId)).balance(balance))
                .persist();

        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(timestamp, treasuryEntity.toEntityId()))
                        .balance(treasuryEntity.getBalance()))
                .persist();
    }

    protected Entity getEntity(EntityId entityId) {
        return entityRepository.findById(entityId.getId()).get();
    }

    protected String getAccountEvmAddress(Entity account) {
        return Bytes.wrap(account.getEvmAddress()).toHexString();
    }

    protected void verifyEthCallAndEstimateGas(
            final RemoteFunctionCall<?> functionCall, final Contract contract, final Long value) throws Exception {
        // Given
        testWeb3jService.setEstimateGas(true);
        functionCall.send();

        final var estimateGasUsedResult = longValueOf.applyAsLong(testWeb3jService.getEstimatedGas());

        // When
        final var actualGasUsed = gasUsedAfterExecution(getContractExecutionParameters(functionCall, contract, value));

        // Then
        assertThat(isWithinExpectedGasRange(estimateGasUsedResult, actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult, actualGasUsed)
                .isTrue();
        testWeb3jService.setEstimateGas(false);
    }
}
