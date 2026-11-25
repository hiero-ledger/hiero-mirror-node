// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.web3.ApiEndpointName.SIMULATE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

import jakarta.annotation.Resource;
import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.rest.model.SimulateCallResult;
import org.hiero.mirror.rest.model.SimulateLog;
import org.hiero.mirror.rest.model.SimulateResponse;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.exception.BlockNumberNotFoundException;
import org.hiero.mirror.web3.service.model.CallServiceParameters;
import org.hiero.mirror.web3.viewmodel.BlockOverride;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hiero.mirror.web3.viewmodel.ContractCallRequest;
import org.hiero.mirror.web3.viewmodel.SimulateBlockStateCall;
import org.hiero.mirror.web3.viewmodel.SimulateRequest;
import org.hiero.mirror.web3.viewmodel.StateOverride;
import org.hiero.mirror.web3.viewmodel.StorageEntry;
import org.hiero.mirror.web3.web3j.generated.Reverter;
import org.hiero.mirror.web3.web3j.generated.StorageContract;
import org.hiero.mirror.web3.web3j.generated.TestNestedAddressThis;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;

class ContractSimulateServiceTest extends AbstractContractCallServiceTest {

    private static final String CONSTRUCTOR_ONLY_INIT_CODE =
            "0x6080604052348015600f57600080fd5b5060a38061001c6000396000f3";
    // PUSH1 0 PUSH1 0 LOG0 STOP: the constructor emits one empty log, exposing the created contract's address.
    private static final String CONSTRUCTOR_LOGGING_INIT_CODE = "0x60006000a000";
    private static final String BLOCK_NUMBER_CONTRACT = "0x00000000000000000000000000000000000b0001";
    // NUMBER PUSH1 0 MSTORE PUSH1 32 PUSH1 0 RETURN: returns block.number.
    private static final String BLOCK_NUMBER_RUNTIME_CODE = "0x4360005260206000f3";
    private static final String FORWARDER = "0x00000000000000000000000000000000000b0002";
    private static final String FORWARDER_TO_REVERTER = "0x00000000000000000000000000000000000b0003";
    private static final String REVERTER = "0x00000000000000000000000000000000000b0004";
    private static final String SELF_DESTRUCTOR = "0x00000000000000000000000000000000000b0006";
    // PUSH1 0 PUSH1 0 REVERT
    private static final String REVERT_RUNTIME_CODE = "0x60006000fd";
    private static final String STORAGE_SLOT_0_KEY =
            "0x0000000000000000000000000000000000000000000000000000000000000000";

    @Resource
    private ContractSimulateService contractSimulateService;

    @MockitoSpyBean
    private TransactionExecutionService transactionExecutionService;

    @Resource
    private Web3Properties web3Properties;

    @Test
    void secondCallInSameEntrySeesFirstCallsStorageMutation() {
        final var contract = testWeb3jService.deploy(StorageContract::deploy);
        final var request = requestWithSingleEntry(setSlot0Call(contract, 42), getSlot0Call(contract));

        final var response = simulate(request);

        final var results = response.getResult();
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getSuccess()).isTrue();
        assertThat(results.get(1).getSuccess()).isTrue();
        assertThat(decodeUint256(results.get(1).getReturnData())).isEqualTo(BigInteger.valueOf(42));
        assertThat(meterRegistry
                        .find(ContractCallService.EVM_INVOCATION_METRIC)
                        .tag(ContractCallService.TAG_TYPE, "ETH_SIMULATE")
                        .counter())
                .isNotNull()
                .extracting(counter -> counter.count())
                .isEqualTo(2.0);
        assertThat(meterRegistry
                        .find(ContractCallService.GAS_LIMIT_METRIC)
                        .tag(ContractCallService.TAG_TYPE, "ETH_SIMULATE")
                        .counter())
                .isNotNull()
                .extracting(counter -> counter.count())
                .isEqualTo(2 * 15_000_000.0);
    }

    @Test
    void stateOverrideAppliesToEveryCallOfTheEntry() {
        final var contract = testWeb3jService.deploy(StorageContract::deploy);
        final var request = requestWithSingleEntry(getSlot0Call(contract), getSlot0Call(contract));
        request.getBlockStateCalls().getFirst().setStateOverrides(List.of(storageOverride(contract, 999)));

        final var response = simulate(request);

        assertThat(response.getResult()).hasSize(2);
        assertThat(decodeUint256(response.getResult().get(0).getReturnData())).isEqualTo(BigInteger.valueOf(999));
        assertThat(decodeUint256(response.getResult().get(1).getReturnData())).isEqualTo(BigInteger.valueOf(999));
    }

    @Test
    void feePayingCallThenAnotherCallSucceeds() {
        final var sender = accountEntityPersistCustomizable(e -> e.balance(DEFAULT_ACCOUNT_BALANCE));
        final var contract = testWeb3jService.deploy(StorageContract::deploy);
        final var write = setSlot0Call(contract, 5);
        write.setFrom(getAliasAddressFromEntity(sender).toHexString());

        final var response = simulate(requestWithSingleEntry(write, getSlot0Call(contract)));

        final var results = response.getResult();
        assertThat(results).extracting(SimulateCallResult::getSuccess).containsExactly(true, true);
        assertThat(decodeUint256(results.get(1).getReturnData())).isEqualTo(BigInteger.valueOf(5));
    }

    @Test
    void revertedCallDoesNotAbortBatchOrLeakStateIntoNextCall() {
        final var storageContract = testWeb3jService.deploy(StorageContract::deploy);
        final var reverter = testWeb3jService.deploy(Reverter::deploy);

        final var setSlot0 = setSlot0Call(storageContract, 7);
        final var revertingCall = new ContractCallRequest();
        revertingCall.setTo(reverter.getContractAddress());
        revertingCall.setData(reverter.send_revertWithString().encodeFunctionCall());
        final var getSlot0 = getSlot0Call(storageContract);

        final var request = requestWithSingleEntry(setSlot0, revertingCall, getSlot0);

        final var response = simulate(request);

        final var results = response.getResult();
        assertThat(results).hasSize(3);
        assertThat(results.get(0).getSuccess()).isTrue();
        assertThat(results.get(0).getError()).isNull();
        assertThat(results.get(1).getSuccess()).isFalse();
        assertThat(results.get(1).getLogs()).isEmpty();
        assertThat(results.get(1).getError().getCode()).isEqualTo(3);
        assertThat(results.get(1).getError().getMessage()).startsWith("execution reverted: ");
        assertThat(results.get(1).getError().getData()).isEqualTo(results.get(1).getReturnData());
        assertThat(results.get(2).getSuccess()).isTrue();
        assertThat(decodeUint256(results.get(2).getReturnData())).isEqualTo(BigInteger.valueOf(7));
    }

    @Test
    void twoContractCreatesInOneEntryGetDistinctAddresses() {
        final var request = requestWithSingleEntry(loggingCreateCall(), loggingCreateCall());

        final var response = simulate(request);

        final var results = response.getResult();
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getSuccess()).isTrue();
        assertThat(results.get(1).getSuccess()).isTrue();
        assertThat(createdAddress(results.get(0))).isNotEqualTo(createdAddress(results.get(1)));
    }

    @Test
    void createWithChildContractThenAnotherCreateSucceeds() {
        final var nestedCreate = new ContractCallRequest();
        nestedCreate.setData(withHexPrefix(TestNestedAddressThis.BINARY));
        final var followUpCreate = new ContractCallRequest();
        followUpCreate.setData(CONSTRUCTOR_ONLY_INIT_CODE);

        final var request = requestWithSingleEntry(nestedCreate, followUpCreate);

        final var response = simulate(request);

        final var results = response.getResult();
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getSuccess()).isTrue();
        assertThat(results.get(1).getSuccess()).isTrue();
    }

    @Test
    void entryIsReportedAsTheAnchorBlock() {
        final var request = requestWithSingleEntry(blockNumberCall(), loggingCreateCall());
        request.getBlockStateCalls()
                .getFirst()
                .setStateOverrides(List.of(codeOverride(BLOCK_NUMBER_CONTRACT, BLOCK_NUMBER_RUNTIME_CODE)));

        final var response = simulate(request);

        final var results = response.getResult();
        final long anchorNumber = genesisRecordFile.getIndex();
        assertThat(decodeUint256(results.get(0).getReturnData())).isEqualTo(BigInteger.valueOf(anchorNumber));
        final var log = createdLog(results.get(1));
        assertThat(log.getBlockNumber()).isEqualTo(anchorNumber);
        assertThat(log.getBlockHash())
                .isEqualTo("0x" + genesisRecordFile.getHash().substring(0, 64));
    }

    @Test
    void blockOverrideSetsTheEntrysBlockNumber() {
        final var request = requestWithSingleEntry(blockNumberCall(), loggingCreateCall());
        final var entry = request.getBlockStateCalls().getFirst();
        entry.setBlockOverride(BlockOverride.builder().blockNumber(256L).build());
        entry.setStateOverrides(List.of(codeOverride(BLOCK_NUMBER_CONTRACT, BLOCK_NUMBER_RUNTIME_CODE)));

        final var response = simulate(request);

        final var results = response.getResult();
        assertThat(decodeUint256(results.get(0).getReturnData())).isEqualTo(BigInteger.valueOf(256));
        final var log = createdLog(results.get(1));
        assertThat(log.getBlockNumber()).isEqualTo(256L);
        assertThat(log.getBlockHash())
                .hasSize(66)
                .isNotEqualTo("0x" + genesisRecordFile.getHash().substring(0, 64));
    }

    @Test
    void traceTransfersCapturesNestedTransfersAndDropsRevertedSubCalls() {
        final var sender = accountEntityPersistCustomizable(e -> e.balance(DEFAULT_ACCOUNT_BALANCE));
        final var senderAddress = getAliasAddressFromEntity(sender).toHexString();
        final var receiverAddress =
                getAliasAddressFromEntity(accountEntityWithEvmAddressPersist()).toHexString();

        final var entry = new SimulateBlockStateCall();
        entry.setStateOverrides(List.of(
                codeOverride(FORWARDER, forwardOneTinybarCode(receiverAddress)),
                codeOverride(REVERTER, REVERT_RUNTIME_CODE),
                codeOverride(FORWARDER_TO_REVERTER, forwardOneTinybarCode(REVERTER))));
        entry.setCalls(List.of(valueCall(senderAddress, FORWARDER), valueCall(senderAddress, FORWARDER_TO_REVERTER)));
        final var request = new SimulateRequest();
        request.setBlockStateCalls(List.of(entry));
        request.setTraceTransfers(true);

        final var response = simulate(request);

        final var results = response.getResult();
        assertThat(results).extracting(SimulateCallResult::getSuccess).containsExactly(true, true);
        // Parent transfer first, then the sub-call's transfer to an account without code.
        assertThat(results.get(0).getLogs())
                .extracting(ContractSimulateServiceTest::describeTransfer)
                .containsExactly(transfer(senderAddress, FORWARDER, 2), transfer(FORWARDER, receiverAddress, 1));
        // Transfer amounts are 32-byte words.
        assertThat(results.get(0).getLogs()).allMatch(log -> log.getData().length() == 66);
        // The reverted sub-call's transfer never happened, so only the parent's remains.
        assertThat(results.get(1).getLogs())
                .extracting(ContractSimulateServiceTest::describeTransfer)
                .containsExactly(transfer(senderAddress, FORWARDER_TO_REVERTER, 2));
    }

    @Test
    void transactionAndLogIndicesCountAcrossTheEntry() {
        final var sender = accountEntityPersistCustomizable(e -> e.balance(DEFAULT_ACCOUNT_BALANCE));
        final var receiver = accountEntityWithEvmAddressPersist();

        final var request = requestWithSingleEntry(transferCall(sender, receiver), transferCall(sender, receiver));
        request.setTraceTransfers(true);

        final var response = simulate(request);

        final var results = response.getResult();
        assertThat(results).hasSize(2);
        final var firstLog = results.get(0).getLogs().getFirst();
        final var secondLog = results.get(1).getLogs().getFirst();
        assertThat(firstLog.getTransactionIndex()).isZero();
        assertThat(firstLog.getIndex()).isZero();
        assertThat(secondLog.getTransactionIndex()).isEqualTo(1L);
        assertThat(secondLog.getIndex()).isEqualTo(1L);
        assertThat(secondLog.getBlockNumber()).isEqualTo(firstLog.getBlockNumber());
        assertThat(secondLog.getTransactionHash()).isNotEqualTo(firstLog.getTransactionHash());
    }

    @Test
    void traceTransfersCapturesSelfDestructBeneficiaryTransfer() {
        final var sender = accountEntityPersistCustomizable(e -> e.balance(DEFAULT_ACCOUNT_BALANCE));
        final var beneficiaryAddress =
                getAliasAddressFromEntity(accountEntityWithEvmAddressPersist()).toHexString();
        final var entry = new SimulateBlockStateCall();
        entry.setStateOverrides(
                List.of(codeOverride(SELF_DESTRUCTOR, "0x73" + beneficiaryAddress.substring(2) + "ff")));
        final var call = new ContractCallRequest();
        call.setFrom(getAliasAddressFromEntity(sender).toHexString());
        call.setTo(SELF_DESTRUCTOR);
        entry.setCalls(List.of(call));
        final var request = new SimulateRequest();
        request.setBlockStateCalls(List.of(entry));
        request.setTraceTransfers(true);

        final var result = simulate(request).getResult().getFirst();

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getLogs())
                .extracting(ContractSimulateServiceTest::describeTransfer)
                .containsExactly(transfer(SELF_DESTRUCTOR, beneficiaryAddress, 100_000_000));
    }

    @Test
    void traceTransfersKeepsSelfDestructWithEmptyStackAnOrdinaryHalt() {
        final var sender = accountEntityPersistCustomizable(e -> e.balance(DEFAULT_ACCOUNT_BALANCE));
        final var entry = new SimulateBlockStateCall();
        entry.setStateOverrides(List.of(codeOverride(SELF_DESTRUCTOR, "0xff")));
        final var call = new ContractCallRequest();
        call.setFrom(getAliasAddressFromEntity(sender).toHexString());
        call.setTo(SELF_DESTRUCTOR);
        entry.setCalls(List.of(call));
        final var request = new SimulateRequest();
        request.setBlockStateCalls(List.of(entry));
        request.setTraceTransfers(true);

        final var result = simulate(request).getResult().getFirst();

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getError().getMessage()).isEqualTo("CONTRACT_EXECUTION_EXCEPTION");
        assertThat(result.getLogs()).isEmpty();
    }

    @Test
    void traceTransfersDisabledProducesNoTransferLog() {
        final var sender = accountEntityPersistCustomizable(e -> e.balance(DEFAULT_ACCOUNT_BALANCE));
        final var receiver = accountEntityWithEvmAddressPersist();

        final var request = requestWithSingleEntry(transferCall(sender, receiver));

        final var response = simulate(request);

        final var results = response.getResult();
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getLogs()).isEmpty();
    }

    @Test
    void successfulCallsKeepTheirUnusedGasRefundable() {
        final var contract = testWeb3jService.deploy(StorageContract::deploy);
        final var firstCall = getSlot0Call(contract);
        firstCall.setGas(1_000_000L);
        final var secondCall = getSlot0Call(contract);
        secondCall.setGas(2_000_000L);
        final var request = requestWithSingleEntry(firstCall, secondCall);

        ContractCallContext.run(context -> {
            context.setRefundableGas(request.totalGas());
            final var results =
                    contractSimulateService.simulate(request, context).getResult();
            final var gasUsed =
                    results.getFirst().getGasUsed() + results.getLast().getGasUsed();
            assertThat(context.getRefundableGas()).isEqualTo(3_000_000L - gasUsed);
            return null;
        });
    }

    @Test
    void unknownBlockFailsRequestAndKeepsAllGasRefundable() {
        final var contract = testWeb3jService.deploy(StorageContract::deploy);
        final var firstCall = getSlot0Call(contract);
        firstCall.setGas(2_000_000L);
        final var secondCall = getSlot0Call(contract);
        secondCall.setGas(3_000_000L);
        final var request = requestWithSingleEntry(firstCall, secondCall);
        request.setBlock(BlockType.of("0x7ffffff0"));

        assertThat(refundableGasAfterFailure(request, BlockNumberNotFoundException.class))
                .isEqualTo(5_000_000L);
    }

    @Test
    void exceededRequestTimeoutFailsRequestAndKeepsUnattemptedGasRefundable() {
        final var contract = testWeb3jService.deploy(StorageContract::deploy);
        final var call = getSlot0Call(contract);
        call.setGas(2_000_000L);
        final var request = requestWithSingleEntry(call);
        final var responseProperties = web3Properties.getApi().get(SIMULATE).getResponse();
        final var timeout = responseProperties.getTimeout();
        responseProperties.setTimeout(Duration.ZERO);

        try {
            assertThat(refundableGasAfterFailure(request, QueryTimeoutException.class))
                    .isEqualTo(2_000_000L);
        } finally {
            responseProperties.setTimeout(timeout);
        }
    }

    @Test
    void unexpectedExecutorFailureFailsRequestAndKeepsUnusedGasRefundable() {
        final var contract = testWeb3jService.deploy(StorageContract::deploy);
        final var firstCall = setSlot0Call(contract, 42);
        firstCall.setGas(2_000_000L);
        final var secondCall = getSlot0Call(contract);
        secondCall.setGas(3_000_123L);
        final var request = requestWithSingleEntry(firstCall, secondCall);

        doThrow(new RuntimeException("unexpected"))
                .when(transactionExecutionService)
                .execute(any(CallServiceParameters.class), anyLong());

        // The failed call used none of its gas.
        assertThat(refundableGasAfterFailure(request, RuntimeException.class)).isEqualTo(5_000_123L);
    }

    @Test
    void invalidInputOnSecondCallFailsRequestAndKeepsUnusedGasRefundable() {
        final var contract = testWeb3jService.deploy(StorageContract::deploy);
        final var firstCall = setSlot0Call(contract, 42);
        firstCall.setGas(1_000_000L);
        final var secondCall = getSlot0Call(contract);
        secondCall.setGas(2_000_000L);
        final var thirdCall = getSlot0Call(contract);
        thirdCall.setGas(3_000_123L);
        final var request = requestWithSingleEntry(firstCall, secondCall, thirdCall);

        doCallRealMethod()
                .doThrow(new BlockNumberNotFoundException())
                .when(transactionExecutionService)
                .execute(any(CallServiceParameters.class), anyLong());

        // The first call's unused gas, all of the failed call's gas and the gas of the call that did not run.
        assertThat(refundableGasAfterFailure(request, BlockNumberNotFoundException.class))
                .isGreaterThan(5_000_123L)
                .isLessThan(6_000_123L);
    }

    // CALL(gas, target, value = 1, no input, no output) then STOP.
    private static String forwardOneTinybarCode(final String target) {
        return "0x6000600060006000600173" + target.substring(2) + "5af100";
    }

    private static StateOverride codeOverride(final String address, final String code) {
        final var stateOverride = new StateOverride();
        stateOverride.setAddress(address);
        stateOverride.setBalance(100_000_000L);
        stateOverride.setCode(code);
        return stateOverride;
    }

    private static ContractCallRequest blockNumberCall() {
        final var call = new ContractCallRequest();
        call.setTo(BLOCK_NUMBER_CONTRACT);
        return call;
    }

    private static ContractCallRequest valueCall(final String from, final String to) {
        final var call = new ContractCallRequest();
        call.setFrom(from);
        call.setTo(to);
        call.setValue(2L);
        return call;
    }

    private static String describeTransfer(final SimulateLog log) {
        return transfer(
                log.getTopics().get(1),
                log.getTopics().get(2),
                new BigInteger(log.getData().substring(2), 16).longValue());
    }

    private static String transfer(final String from, final String to, final long value) {
        return "%s->%s:%d".formatted(lastAddressBytes(from), lastAddressBytes(to), value);
    }

    private static String lastAddressBytes(final String addressOrTopic) {
        return addressOrTopic.substring(addressOrTopic.length() - 40).toLowerCase();
    }

    private static ContractCallRequest loggingCreateCall() {
        final var call = new ContractCallRequest();
        call.setData(CONSTRUCTOR_LOGGING_INIT_CODE);
        return call;
    }

    private static String createdAddress(final SimulateCallResult result) {
        return createdLog(result).getAddress();
    }

    private static SimulateLog createdLog(final SimulateCallResult result) {
        assertThat(result.getLogs()).hasSize(1);
        return result.getLogs().getFirst();
    }

    private ContractCallRequest transferCall(final Entity sender, final Entity receiver) {
        final var call = new ContractCallRequest();
        call.setFrom(getAliasAddressFromEntity(sender).toHexString());
        call.setTo(getAliasAddressFromEntity(receiver).toHexString());
        call.setValue(1000L);
        return call;
    }

    private ContractCallRequest setSlot0Call(final StorageContract contract, final long value) {
        final var call = new ContractCallRequest();
        call.setTo(contract.getContractAddress());
        call.setData(contract.send_setSlot0(BigInteger.valueOf(value)).encodeFunctionCall());
        return call;
    }

    private ContractCallRequest getSlot0Call(final StorageContract contract) {
        final var call = new ContractCallRequest();
        call.setTo(contract.getContractAddress());
        call.setData(contract.call_slot0().encodeFunctionCall());
        call.setFrom(Address.ZERO.toHexString());
        return call;
    }

    private StorageEntry storageEntry(final long value) {
        final var entry = new StorageEntry();
        entry.setKey(STORAGE_SLOT_0_KEY);
        entry.setValue("0x" + "%064x".formatted(value));
        return entry;
    }

    private StateOverride storageOverride(final StorageContract contract, final long value) {
        final var stateOverride = new StateOverride();
        stateOverride.setAddress(contract.getContractAddress());
        stateOverride.setState(List.of(storageEntry(value)));
        return stateOverride;
    }

    private static String withHexPrefix(final String hex) {
        return hex.startsWith("0x") ? hex : "0x" + hex;
    }

    private long refundableGasAfterFailure(final SimulateRequest request, final Class<? extends Throwable> failure) {
        return ContractCallContext.run(context -> {
            context.setRefundableGas(request.totalGas());
            assertThatThrownBy(() -> contractSimulateService.simulate(request, context))
                    .isInstanceOf(failure);
            return context.getRefundableGas();
        });
    }

    private SimulateResponse simulate(final SimulateRequest request) {
        return ContractCallContext.run(context -> {
            context.setRefundableGas(request.totalGas());
            return contractSimulateService.simulate(request, context);
        });
    }

    private SimulateRequest requestWithSingleEntry(final ContractCallRequest... calls) {
        final var entry = new SimulateBlockStateCall();
        entry.setCalls(List.of(calls));
        final var request = new SimulateRequest();
        request.setBlockStateCalls(List.of(entry));
        return request;
    }

    @SuppressWarnings("unchecked")
    private static BigInteger decodeUint256(final String hexResult) {
        return ((Uint256) FunctionReturnDecoder.decode(
                                hexResult, List.of(TypeReference.create((Class<Type>) (Class<?>) Uint256.class)))
                        .get(0))
                .getValue();
    }
}
