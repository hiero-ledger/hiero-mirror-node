// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import jakarta.annotation.Resource;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.web3.exception.BlockNumberNotFoundException;
import org.hiero.mirror.web3.service.model.CallServiceParameters;
import org.hiero.mirror.web3.throttle.ThrottleManager;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hiero.mirror.web3.viewmodel.SimulateBlockStateCall;
import org.hiero.mirror.web3.viewmodel.SimulateCall;
import org.hiero.mirror.web3.viewmodel.SimulateCallResult;
import org.hiero.mirror.web3.viewmodel.SimulateLog;
import org.hiero.mirror.web3.viewmodel.SimulateRequest;
import org.hiero.mirror.web3.viewmodel.StateOverride;
import org.hiero.mirror.web3.viewmodel.StorageEntry;
import org.hiero.mirror.web3.web3j.generated.Reverter;
import org.hiero.mirror.web3.web3j.generated.StorageContract;
import org.hiero.mirror.web3.web3j.generated.TestNestedAddressThis;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
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
    // PUSH1 0 PUSH1 0 REVERT
    private static final String REVERT_RUNTIME_CODE = "0x60006000fd";
    private static final String STORAGE_SLOT_0_KEY =
            "0x0000000000000000000000000000000000000000000000000000000000000000";

    @Resource
    private ContractSimulateService contractSimulateService;

    @MockitoSpyBean
    private ThrottleManager throttleManager;

    @MockitoSpyBean
    private TransactionExecutionService transactionExecutionService;

    @Test
    void secondCallInSameEntrySeesFirstCallsStorageMutation() {
        final var contract = testWeb3jService.deploy(StorageContract::deploy);
        final var request = requestWithSingleEntry(setSlot0Call(contract, 42), getSlot0Call(contract));

        final var response = contractSimulateService.simulate(request);

        final var results = response.result();
        assertThat(results).hasSize(2);
        assertThat(results.get(0).status()).isEqualTo("0x1");
        assertThat(results.get(1).status()).isEqualTo("0x1");
        assertThat(decodeUint256(results.get(1).returnData())).isEqualTo(BigInteger.valueOf(42));
        assertThat(meterRegistry
                        .find(ContractCallService.EVM_INVOCATION_METRIC)
                        .tag(ContractCallService.TAG_TYPE, "ETH_SIMULATE")
                        .counter())
                .isNotNull()
                .extracting(counter -> counter.count())
                .isEqualTo(2.0);
    }

    @Test
    void realStateResetsBetweenEntries() {
        final var contract = testWeb3jService.deploy(StorageContract::deploy);
        final var request = requestWithEntries(List.of(setSlot0Call(contract, 42)), List.of(getSlot0Call(contract)));

        final var response = contractSimulateService.simulate(request);

        assertThat(response.result()).hasSize(2);
        assertThat(response.result().get(0).status()).isEqualTo("0x1");
        assertThat(decodeUint256(response.result().get(1).returnData())).isEqualTo(BigInteger.ZERO);
    }

    @Test
    void stateOverridePersistsIntoLaterEntries() {
        final var contract = testWeb3jService.deploy(StorageContract::deploy);

        final var overriddenEntry = new SimulateBlockStateCall();
        overriddenEntry.setCalls(List.of(getSlot0Call(contract)));
        overriddenEntry.setStateOverrides(List.of(storageOverride(contract, 999)));

        final var plainEntry = new SimulateBlockStateCall();
        plainEntry.setCalls(List.of(getSlot0Call(contract)));

        final var request = new SimulateRequest();
        request.setBlockStateCalls(List.of(overriddenEntry, plainEntry));

        final var response = contractSimulateService.simulate(request);

        assertThat(response.result()).hasSize(2);
        assertThat(decodeUint256(response.result().get(0).returnData())).isEqualTo(BigInteger.valueOf(999));
        assertThat(decodeUint256(response.result().get(1).returnData())).isEqualTo(BigInteger.valueOf(999));
    }

    @Test
    void revertedCallDoesNotAbortBatchOrLeakStateIntoNextCall() {
        final var storageContract = testWeb3jService.deploy(StorageContract::deploy);
        final var reverter = testWeb3jService.deploy(Reverter::deploy);

        final var setSlot0 = setSlot0Call(storageContract, 7);
        final var revertingCall = new SimulateCall();
        revertingCall.setTo(reverter.getContractAddress());
        revertingCall.setData(reverter.send_revertWithString().encodeFunctionCall());
        final var getSlot0 = getSlot0Call(storageContract);

        final var request = requestWithSingleEntry(setSlot0, revertingCall, getSlot0);

        final var response = contractSimulateService.simulate(request);

        final var results = response.result();
        assertThat(results).hasSize(3);
        assertThat(results.get(0).status()).isEqualTo("0x1");
        assertThat(results.get(1).status()).isEqualTo("0x0");
        assertThat(results.get(1).logs()).isEmpty();
        assertThat(results.get(2).status()).isEqualTo("0x1");
        assertThat(decodeUint256(results.get(2).returnData())).isEqualTo(BigInteger.valueOf(7));
    }

    @Test
    void twoContractCreatesInOneEntryGetDistinctAddresses() {
        final var request = requestWithSingleEntry(loggingCreateCall(), loggingCreateCall());

        final var response = contractSimulateService.simulate(request);

        final var results = response.result();
        assertThat(results).hasSize(2);
        assertThat(results.get(0).status()).isEqualTo("0x1");
        assertThat(results.get(1).status()).isEqualTo("0x1");
        assertThat(createdAddress(results.get(0))).isNotEqualTo(createdAddress(results.get(1)));
    }

    @Test
    void createWithChildContractThenAnotherCreateSucceeds() {
        final var nestedCreate = new SimulateCall();
        nestedCreate.setData(withHexPrefix(TestNestedAddressThis.BINARY));
        final var followUpCreate = new SimulateCall();
        followUpCreate.setData(CONSTRUCTOR_ONLY_INIT_CODE);

        final var request = requestWithSingleEntry(nestedCreate, followUpCreate);

        final var response = contractSimulateService.simulate(request);

        final var results = response.result();
        assertThat(results).hasSize(2);
        assertThat(results.get(0).status()).isEqualTo("0x1");
        assertThat(results.get(1).status()).isEqualTo("0x1");
    }

    @Test
    void createsInSeparateEntriesGetDistinctAddressesDespiteStateReset() {
        final var request = requestWithEntries(List.of(loggingCreateCall()), List.of(loggingCreateCall()));

        final var response = contractSimulateService.simulate(request);

        final var results = response.result();
        assertThat(results).hasSize(2);
        assertThat(results.get(0).status()).isEqualTo("0x1");
        assertThat(results.get(1).status()).isEqualTo("0x1");
        assertThat(createdAddress(results.get(0))).isNotEqualTo(createdAddress(results.get(1)));
    }

    @Test
    void eachEntryRunsAndIsReportedAsItsOwnBlockAfterTheAnchor() {
        final var firstEntry = new SimulateBlockStateCall();
        firstEntry.setStateOverrides(List.of(codeOverride(BLOCK_NUMBER_CONTRACT, BLOCK_NUMBER_RUNTIME_CODE)));
        firstEntry.setCalls(List.of(blockNumberCall(), loggingCreateCall()));
        final var secondEntry = new SimulateBlockStateCall();
        secondEntry.setCalls(List.of(blockNumberCall(), loggingCreateCall()));
        final var request = new SimulateRequest();
        request.setBlockStateCalls(List.of(firstEntry, secondEntry));

        final var response = contractSimulateService.simulate(request);

        final var results = response.result();
        assertThat(results).hasSize(4);
        final long anchorNumber = genesisRecordFile.getIndex();
        assertThat(decodeUint256(results.get(0).returnData())).isEqualTo(BigInteger.valueOf(anchorNumber));
        assertThat(decodeUint256(results.get(2).returnData())).isEqualTo(BigInteger.valueOf(anchorNumber + 1));
        final var firstEntryLog = createdLog(results.get(1));
        final var secondEntryLog = createdLog(results.get(3));
        assertThat(firstEntryLog.blockNumber()).isEqualTo(anchorNumber);
        assertThat(firstEntryLog.blockHash())
                .isEqualTo("0x" + genesisRecordFile.getHash().substring(0, 64));
        assertThat(secondEntryLog.blockNumber()).isEqualTo(anchorNumber + 1);
        assertThat(secondEntryLog.blockHash()).hasSize(66).isNotEqualTo(firstEntryLog.blockHash());
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

        final var response = contractSimulateService.simulate(request);

        final var results = response.result();
        assertThat(results).extracting(SimulateCallResult::status).containsExactly("0x1", "0x1");
        // Parent transfer first, then the sub-call's transfer to an account without code.
        assertThat(results.get(0).logs())
                .extracting(ContractSimulateServiceTest::describeTransfer)
                .containsExactly(transfer(senderAddress, FORWARDER, 2), transfer(FORWARDER, receiverAddress, 1));
        // Transfer amounts are 32-byte ABI words, as in the HIP example.
        assertThat(results.get(0).logs()).allMatch(log -> log.data().length() == 66);
        // The reverted sub-call's transfer never happened, so only the parent's remains.
        assertThat(results.get(1).logs())
                .extracting(ContractSimulateServiceTest::describeTransfer)
                .containsExactly(transfer(senderAddress, FORWARDER_TO_REVERTER, 2));
    }

    @Test
    void transactionAndLogIndicesRestartPerEntry() {
        final var sender = accountEntityPersistCustomizable(e -> e.balance(DEFAULT_ACCOUNT_BALANCE));
        final var receiver = accountEntityWithEvmAddressPersist();

        final var request = requestWithEntries(
                List.of(transferCall(sender, receiver), transferCall(sender, receiver)),
                List.of(transferCall(sender, receiver)));
        request.setTraceTransfers(true);

        final var response = contractSimulateService.simulate(request);

        final var results = response.result();
        assertThat(results).hasSize(3);

        final var firstEntryFirstLog = results.get(0).logs().getFirst();
        final var firstEntrySecondLog = results.get(1).logs().getFirst();
        final var secondEntryLog = results.get(2).logs().getFirst();

        assertThat(firstEntryFirstLog.transactionIndex()).isZero();
        assertThat(firstEntryFirstLog.logIndex()).isZero();
        assertThat(firstEntrySecondLog.transactionIndex()).isEqualTo(1L);
        assertThat(firstEntrySecondLog.logIndex()).isEqualTo(1L);
        assertThat(secondEntryLog.transactionIndex()).isZero();
        assertThat(secondEntryLog.logIndex()).isZero();
        assertThat(secondEntryLog.blockNumber()).isEqualTo(firstEntryFirstLog.blockNumber() + 1);
        assertThat(secondEntryLog.transactionHash()).isNotEqualTo(firstEntryFirstLog.transactionHash());
    }

    @Test
    void traceTransfersDisabledProducesNoTransferLog() {
        final var sender = accountEntityPersistCustomizable(e -> e.balance(DEFAULT_ACCOUNT_BALANCE));
        final var receiver = accountEntityWithEvmAddressPersist();

        final var request = requestWithSingleEntry(transferCall(sender, receiver));

        final var response = contractSimulateService.simulate(request);

        final var results = response.result();
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().logs()).isEmpty();
    }

    @Test
    void unknownBlockFailsRequestAndRestoresAllGas() {
        final var contract = testWeb3jService.deploy(StorageContract::deploy);
        final var firstCall = getSlot0Call(contract);
        firstCall.setGas(2_000_000L);
        final var secondCall = getSlot0Call(contract);
        secondCall.setGas(3_000_000L);
        final var request = requestWithSingleEntry(firstCall, secondCall);
        request.setBlock(BlockType.of("0x7ffffff0"));

        assertThatThrownBy(() -> contractSimulateService.simulate(request))
                .isInstanceOf(BlockNumberNotFoundException.class);

        verify(throttleManager).restore(5_000_000L);
    }

    @Test
    void unexpectedExecutorFailureRevertsCallWithoutFailingBatch() {
        final var contract = testWeb3jService.deploy(StorageContract::deploy);
        final var request = requestWithSingleEntry(setSlot0Call(contract, 42), getSlot0Call(contract));

        doThrow(new RuntimeException("unexpected"))
                .doCallRealMethod()
                .when(transactionExecutionService)
                .execute(any(CallServiceParameters.class), anyLong());

        final var response = contractSimulateService.simulate(request);

        final var results = response.result();
        assertThat(results).hasSize(2);
        assertThat(results.get(0).status()).isEqualTo("0x0");
        assertThat(results.get(0).returnData()).isEqualTo("0x");
        assertThat(results.get(1).status()).isEqualTo("0x1");
    }

    // CALL(gas, target, value = 1, no input, no output) then STOP.
    private static String forwardOneTinybarCode(final String target) {
        return "0x6000600060006000600173" + target.substring(2) + "5af100";
    }

    private static StateOverride codeOverride(final String address, final String code) {
        final var stateOverride = new StateOverride();
        stateOverride.setAddress(address);
        stateOverride.setBalance("0x5f5e100");
        stateOverride.setCode(code);
        return stateOverride;
    }

    private static SimulateCall blockNumberCall() {
        final var call = new SimulateCall();
        call.setTo(BLOCK_NUMBER_CONTRACT);
        return call;
    }

    private static SimulateCall valueCall(final String from, final String to) {
        final var call = new SimulateCall();
        call.setFrom(from);
        call.setTo(to);
        call.setValue(2L);
        return call;
    }

    private static String describeTransfer(final SimulateLog log) {
        return transfer(
                log.topics().get(1),
                log.topics().get(2),
                new BigInteger(log.data().substring(2), 16).longValue());
    }

    private static String transfer(final String from, final String to, final long value) {
        return "%s->%s:%d".formatted(lastAddressBytes(from), lastAddressBytes(to), value);
    }

    private static String lastAddressBytes(final String addressOrTopic) {
        return addressOrTopic.substring(addressOrTopic.length() - 40).toLowerCase();
    }

    private static SimulateCall loggingCreateCall() {
        final var call = new SimulateCall();
        call.setData(CONSTRUCTOR_LOGGING_INIT_CODE);
        return call;
    }

    private static String createdAddress(final SimulateCallResult result) {
        return createdLog(result).address();
    }

    private static SimulateLog createdLog(final SimulateCallResult result) {
        assertThat(result.logs()).hasSize(1);
        return result.logs().getFirst();
    }

    private SimulateCall transferCall(final Entity sender, final Entity receiver) {
        final var call = new SimulateCall();
        call.setFrom(getAliasAddressFromEntity(sender).toHexString());
        call.setTo(getAliasAddressFromEntity(receiver).toHexString());
        call.setValue(1000L);
        return call;
    }

    private SimulateCall setSlot0Call(final StorageContract contract, final long value) {
        final var call = new SimulateCall();
        call.setTo(contract.getContractAddress());
        call.setData(contract.send_setSlot0(BigInteger.valueOf(value)).encodeFunctionCall());
        return call;
    }

    private SimulateCall getSlot0Call(final StorageContract contract) {
        final var call = new SimulateCall();
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

    private SimulateRequest requestWithSingleEntry(final SimulateCall... calls) {
        return requestWithEntries(List.of(calls));
    }

    @SafeVarargs
    private SimulateRequest requestWithEntries(final List<SimulateCall>... entryCalls) {
        final var entries = new ArrayList<SimulateBlockStateCall>(entryCalls.length);
        for (final var calls : entryCalls) {
            final var entry = new SimulateBlockStateCall();
            entry.setCalls(calls);
            entries.add(entry);
        }
        final var request = new SimulateRequest();
        request.setBlockStateCalls(entries);
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
