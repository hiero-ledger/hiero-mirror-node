// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.annotation.Resource;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.web3.viewmodel.SimulateBlockStateCall;
import org.hiero.mirror.web3.viewmodel.SimulateCall;
import org.hiero.mirror.web3.viewmodel.SimulateRequest;
import org.hiero.mirror.web3.viewmodel.StateOverride;
import org.hiero.mirror.web3.viewmodel.StorageEntry;
import org.hiero.mirror.web3.web3j.generated.Reverter;
import org.hiero.mirror.web3.web3j.generated.StorageContract;
import org.hiero.mirror.web3.web3j.generated.TestNestedAddressThis;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;

class ContractSimulateServiceTest extends AbstractContractCallServiceTest {

    private static final String CONSTRUCTOR_ONLY_INIT_CODE =
            "0x6080604052348015600f57600080fd5b5060a38061001c6000396000f3";
    private static final String STORAGE_SLOT_0_KEY =
            "0x0000000000000000000000000000000000000000000000000000000000000000";

    @Resource
    private ContractSimulateService contractSimulateService;

    @Test
    void singleCallSucceeds() {
        final var contract = testWeb3jService.deploy(StorageContract::deploy);
        final var request = requestWithSingleEntry(setSlot0Call(contract, 42));

        final var response = contractSimulateService.simulate(request);

        assertThat(response.result()).hasSize(1);
        assertThat(response.result().getFirst()).hasSize(1);
        assertThat(response.result().getFirst().getFirst().status()).isEqualTo("0x1");
    }

    @Test
    void secondCallInSameEntrySeesFirstCallsStorageMutation() {
        final var contract = testWeb3jService.deploy(StorageContract::deploy);
        final var request = requestWithSingleEntry(setSlot0Call(contract, 42), getSlot0Call(contract));

        final var response = contractSimulateService.simulate(request);

        final var entryResults = response.result().getFirst();
        assertThat(entryResults).hasSize(2);
        assertThat(entryResults.get(0).status()).isEqualTo("0x1");
        assertThat(entryResults.get(1).status()).isEqualTo("0x1");
        assertThat(decodeUint256(entryResults.get(1).returnData())).isEqualTo(BigInteger.valueOf(42));
    }

    @Test
    void realStateResetsBetweenEntries() {
        final var contract = testWeb3jService.deploy(StorageContract::deploy);
        final var request = requestWithEntries(List.of(setSlot0Call(contract, 42)), List.of(getSlot0Call(contract)));

        final var response = contractSimulateService.simulate(request);

        assertThat(response.result()).hasSize(2);
        assertThat(response.result().get(0).getFirst().status()).isEqualTo("0x1");
        assertThat(decodeUint256(response.result().get(1).getFirst().returnData()))
                .isEqualTo(BigInteger.ZERO);
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
        assertThat(decodeUint256(response.result().get(0).getFirst().returnData()))
                .isEqualTo(BigInteger.valueOf(999));
        assertThat(decodeUint256(response.result().get(1).getFirst().returnData()))
                .isEqualTo(BigInteger.valueOf(999));
    }

    @Test
    void callsWithinOverrideEntryStayCumulative() {
        final var contract = testWeb3jService.deploy(StorageContract::deploy);

        final var overriddenEntry = new SimulateBlockStateCall();
        overriddenEntry.setCalls(List.of(getSlot0Call(contract), setSlot0Call(contract, 5), getSlot0Call(contract)));
        overriddenEntry.setStateOverrides(List.of(storageOverride(contract, 999)));

        final var plainEntry = new SimulateBlockStateCall();
        plainEntry.setCalls(List.of(getSlot0Call(contract)));

        final var request = new SimulateRequest();
        request.setBlockStateCalls(List.of(overriddenEntry, plainEntry));

        final var response = contractSimulateService.simulate(request);

        assertThat(response.result()).hasSize(2);
        final var overriddenEntryResults = response.result().get(0);
        assertThat(overriddenEntryResults).hasSize(3);
        assertThat(decodeUint256(overriddenEntryResults.get(0).returnData())).isEqualTo(BigInteger.valueOf(999));
        assertThat(overriddenEntryResults.get(1).status()).isEqualTo("0x1");
        assertThat(decodeUint256(overriddenEntryResults.get(2).returnData())).isEqualTo(BigInteger.valueOf(5));
        assertThat(decodeUint256(response.result().get(1).getFirst().returnData()))
                .isEqualTo(BigInteger.valueOf(999));
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

        final var entryResults = response.result().getFirst();
        assertThat(entryResults).hasSize(3);
        assertThat(entryResults.get(0).status()).isEqualTo("0x1");
        assertThat(entryResults.get(1).status()).isEqualTo("0x0");
        assertThat(entryResults.get(1).logs()).isEmpty();
        assertThat(entryResults.get(2).status()).isEqualTo("0x1");
        assertThat(decodeUint256(entryResults.get(2).returnData())).isEqualTo(BigInteger.valueOf(7));
    }

    @Test
    void twoContractCreatesInOneEntryGetDistinctAddresses() {
        final var firstCreate = new SimulateCall();
        firstCreate.setData(CONSTRUCTOR_ONLY_INIT_CODE);
        final var secondCreate = new SimulateCall();
        secondCreate.setData(CONSTRUCTOR_ONLY_INIT_CODE);

        final var request = requestWithSingleEntry(firstCreate, secondCreate);

        final var response = contractSimulateService.simulate(request);

        final var entryResults = response.result().getFirst();
        assertThat(entryResults).hasSize(2);
        assertThat(entryResults.get(0).status()).isEqualTo("0x1");
        assertThat(entryResults.get(1).status()).isEqualTo("0x1");
    }

    @Test
    void createWithChildContractThenAnotherCreateSucceeds() {
        final var nestedCreate = new SimulateCall();
        nestedCreate.setData(withHexPrefix(TestNestedAddressThis.BINARY));
        final var followUpCreate = new SimulateCall();
        followUpCreate.setData(CONSTRUCTOR_ONLY_INIT_CODE);

        final var request = requestWithSingleEntry(nestedCreate, followUpCreate);

        final var response = contractSimulateService.simulate(request);

        final var entryResults = response.result().getFirst();
        assertThat(entryResults).hasSize(2);
        assertThat(entryResults.get(0).status()).isEqualTo("0x1");
        assertThat(entryResults.get(1).status()).isEqualTo("0x1");
    }

    @Test
    void createsInSeparateEntriesSucceedDespiteStateReset() {
        final var firstCreate = new SimulateCall();
        firstCreate.setData(CONSTRUCTOR_ONLY_INIT_CODE);
        final var secondCreate = new SimulateCall();
        secondCreate.setData(CONSTRUCTOR_ONLY_INIT_CODE);

        final var request = requestWithEntries(List.of(firstCreate), List.of(secondCreate));

        final var response = contractSimulateService.simulate(request);

        assertThat(response.result()).hasSize(2);
        assertThat(response.result().get(0).getFirst().status()).isEqualTo("0x1");
        assertThat(response.result().get(1).getFirst().status()).isEqualTo("0x1");
    }

    @Test
    void traceTransfersCapturesValueTransferAsLog() {
        final var sender = accountEntityPersistCustomizable(e -> e.balance(DEFAULT_ACCOUNT_BALANCE));
        final var receiver = accountEntityWithEvmAddressPersist();

        final var call = new SimulateCall();
        call.setFrom(getAliasAddressFromEntity(sender).toHexString());
        call.setTo(getAliasAddressFromEntity(receiver).toHexString());
        call.setValue(1000L);

        final var request = requestWithSingleEntry(call);
        request.setTraceTransfers(true);

        final var response = contractSimulateService.simulate(request);

        final var entryResults = response.result().getFirst();
        assertThat(entryResults).hasSize(1);
        assertThat(entryResults.getFirst().status()).isEqualTo("0x1");
        assertThat(entryResults.getFirst().logs()).hasSize(1);
        final var log = entryResults.getFirst().logs().getFirst();
        assertThat(log.address()).isEqualToIgnoringCase("0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");
        assertThat(log.topics()).hasSize(3);
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

        assertThat(response.result()).hasSize(2);
        final var firstEntryResults = response.result().get(0);
        final var secondEntryResults = response.result().get(1);

        final var firstEntryFirstLog = firstEntryResults.get(0).logs().getFirst();
        final var firstEntrySecondLog = firstEntryResults.get(1).logs().getFirst();
        final var secondEntryLog = secondEntryResults.getFirst().logs().getFirst();

        assertThat(firstEntryFirstLog.transactionIndex()).isEqualTo("0x0");
        assertThat(firstEntryFirstLog.logIndex()).isEqualTo("0x0");
        assertThat(firstEntrySecondLog.transactionIndex()).isEqualTo("0x1");
        assertThat(firstEntrySecondLog.logIndex()).isEqualTo("0x1");
        assertThat(secondEntryLog.transactionIndex()).isEqualTo("0x0");
        assertThat(secondEntryLog.logIndex()).isEqualTo("0x0");
        assertThat(secondEntryLog.transactionHash()).isNotEqualTo(firstEntryFirstLog.transactionHash());
    }

    @Test
    void traceTransfersDisabledProducesNoTransferLog() {
        final var sender = accountEntityPersistCustomizable(e -> e.balance(DEFAULT_ACCOUNT_BALANCE));
        final var receiver = accountEntityWithEvmAddressPersist();

        final var request = requestWithSingleEntry(transferCall(sender, receiver));

        final var response = contractSimulateService.simulate(request);

        final var entryResults = response.result().getFirst();
        assertThat(entryResults).hasSize(1);
        assertThat(entryResults.getFirst().logs()).isEmpty();
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
