// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.leftPadBytes;
import static org.hiero.mirror.web3.convert.BytesDecoder.hexToBytes;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.ContractID.ContractOneOfType;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.service.ContractStateService;
import org.hiero.mirror.web3.state.CommonEntityAccessor;
import org.hiero.mirror.web3.viewmodel.StateOverride;
import org.hiero.mirror.web3.viewmodel.StorageEntry;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractStorageReadableKVStateTest {

    private static final ContractID CONTRACT_ID =
            new ContractID(1L, 0L, new OneOf<>(ContractOneOfType.CONTRACT_NUM, 1L));
    private static final String CONTRACT_ID_WITH_NUM_ADDRESS =
            toAddress(CONTRACT_ID.contractNum()).toUnprefixedHexString();
    private static final String MISSING_CONTRACT_ADDRESS = "0x00000000000000000000000000000000000000aa";
    private static final ContractID MISSING_CONTRACT_ID = new ContractID(
            1L, 0L, new OneOf<>(ContractOneOfType.EVM_ADDRESS, Bytes.fromHex(MISSING_CONTRACT_ADDRESS.substring(2))));
    private static final Bytes BYTES = Bytes.wrap(leftPadBytes("123456".getBytes(), Bytes32.SIZE));
    private static final SlotKey SLOT_KEY = new SlotKey(CONTRACT_ID, BYTES);
    private static final EntityId ENTITY_ID =
            EntityId.of(CONTRACT_ID.shardNum(), CONTRACT_ID.realmNum(), CONTRACT_ID.contractNum());
    private static final Address EVM_ALIAS_ADDRESS =
            Address.fromHexString("0xb794f5ea0ba39494ce839613fffba74279579268");
    private static final String SLOT_KEY_HEX = "0x0000000000000000000000000000000000000000000000000000313233343536";
    private static final String OTHER_SLOT_KEY_HEX =
            "0x0000000000000000000000000000000000000000000000000000000000000002";
    private static final String OVERRIDE_VALUE_HEX =
            "0x0000000000000000000000000000000000000000000000000000000000000064";
    private static final SlotValue OVERRIDE_SLOT_VALUE = new SlotValue(
            Bytes.wrap(leftPadBytes(hexToBytes(OVERRIDE_VALUE_HEX), Bytes32.SIZE)), Bytes.EMPTY, Bytes.EMPTY);
    private static final SlotValue DATABASE_SLOT_VALUE = new SlotValue(BYTES, Bytes.EMPTY, Bytes.EMPTY);
    private static final SlotKey MISSING_SLOT_KEY = new SlotKey(MISSING_CONTRACT_ID, BYTES);
    private static MockedStatic<ContractCallContext> contextMockedStatic;

    @InjectMocks
    private ContractStorageReadableKVState contractStorageReadableKVState;

    @Mock
    private ContractStateService contractStateService;

    @Mock
    private CommonEntityAccessor commonEntityAccessor;

    @Spy
    private ContractCallContext contractCallContext;

    @BeforeAll
    static void initStaticMocks() {
        contextMockedStatic = mockStatic(ContractCallContext.class);
    }

    @AfterAll
    static void closeStaticMocks() {
        contextMockedStatic.close();
    }

    @BeforeEach
    void setup() {
        contextMockedStatic.when(ContractCallContext::get).thenReturn(contractCallContext);
    }

    @Test
    void whenTimestampIsNullReturnsLatestSlot() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(contractStateService.findStorage(ENTITY_ID, BYTES.toByteArray()))
                .thenReturn(Optional.of(BYTES.toByteArray()));
        assertThat(contractStorageReadableKVState.get(SLOT_KEY))
                .satisfies(slotValue -> assertThat(slotValue).returns(BYTES, SlotValue::value));
    }

    @Test
    void whenTimestampIsNotNullReturnsHistoricalSlot() {
        final var blockTimestamp = 1234567L;
        when(contractCallContext.getTimestamp()).thenReturn(Optional.of(blockTimestamp));
        when(contractStateService.findStorageByBlockTimestamp(
                        ENTITY_ID,
                        Bytes32.wrap(BYTES.toByteArray()).trimLeadingZeros().toArrayUnsafe(),
                        blockTimestamp))
                .thenReturn(Optional.of(BYTES.toByteArray()));
        assertThat(contractStorageReadableKVState.get(SLOT_KEY))
                .satisfies(slotValue -> assertThat(slotValue).returns(BYTES, SlotValue::value));
    }

    @Test
    void whenSlotNotFoundReturnsNullForLatestBlock() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(contractStateService.findStorage(any(), any())).thenReturn(Optional.empty());
        assertThat(contractStorageReadableKVState.get(SLOT_KEY))
                .satisfies(slotValue -> assertThat(slotValue).isNull());
    }

    @Test
    void whenSlotNotFoundReturnsNullForHistoricalBlock() {
        final var blockTimestamp = 1234567L;
        when(contractCallContext.getTimestamp()).thenReturn(Optional.of(blockTimestamp));
        when(contractStateService.findStorageByBlockTimestamp(any(), any(), anyLong()))
                .thenReturn(Optional.empty());
        assertThat(contractStorageReadableKVState.get(SLOT_KEY))
                .satisfies(slotValue -> assertThat(slotValue).isNull());
    }

    @Test
    void whenSlotKeyIsNullReturnNull() {
        assertThat(contractStorageReadableKVState.get(new SlotKey(null, BYTES)))
                .satisfies(slotValue -> assertThat(slotValue).isNull());
    }

    @Test
    void whenStateOverrideHasFullStateReturnsNullForUnlistedSlot() {
        contractCallContext.setStateOverrides(Map.of(
                Bytes.fromHex(CONTRACT_ID_WITH_NUM_ADDRESS),
                stateOverrideWithState(OTHER_SLOT_KEY_HEX, OVERRIDE_VALUE_HEX)));

        assertThat(contractStorageReadableKVState.get(SLOT_KEY)).isNull();
        verify(contractStateService, never()).findStorage(any(), any());
    }

    @Test
    void whenStateOverrideHasFullStateTakesPrecedenceOverDatabase() {
        contractCallContext.setStateOverrides(Map.of(
                Bytes.fromHex(CONTRACT_ID_WITH_NUM_ADDRESS), stateOverrideWithState(SLOT_KEY_HEX, OVERRIDE_VALUE_HEX)));
        lenient()
                .when(contractStateService.findStorage(ENTITY_ID, BYTES.toByteArray()))
                .thenReturn(Optional.of(BYTES.toByteArray()));

        assertThat(contractStorageReadableKVState.get(SLOT_KEY)).isEqualTo(OVERRIDE_SLOT_VALUE);
        verify(contractStateService, never()).findStorage(ENTITY_ID, BYTES.toByteArray());
    }

    @Test
    void whenAliasFoundInDBReturnsOverride() {
        // The override is keyed by the contract's non-long-zero EVM alias even though the lookup uses contractNum.
        contractCallContext.setStateOverrides(Map.of(
                Bytes.wrap(EVM_ALIAS_ADDRESS.toArrayUnsafe()),
                stateOverrideWithState(SLOT_KEY_HEX, OVERRIDE_VALUE_HEX)));
        when(commonEntityAccessor.evmAddressFromId(ENTITY_ID, Optional.empty())).thenReturn(EVM_ALIAS_ADDRESS);

        assertThat(contractStorageReadableKVState.get(SLOT_KEY)).isEqualTo(OVERRIDE_SLOT_VALUE);
        verify(contractStateService, never()).findStorage(any(), any());
    }

    @Test
    void whenMissingContractAndOverrideHasStateReturnsOverride() {
        contractCallContext.setStateOverrides(Map.of(
                Bytes.fromHex(MISSING_CONTRACT_ADDRESS.substring(2)),
                stateOverrideWithState(SLOT_KEY_HEX, OVERRIDE_VALUE_HEX)));

        assertThat(contractStorageReadableKVState.get(MISSING_SLOT_KEY)).isEqualTo(OVERRIDE_SLOT_VALUE);
        verify(contractStateService, never()).findStorage(any(), any());
        verify(contractStateService, never()).findStorageByBlockTimestamp(any(), any(), anyLong());
    }

    @Test
    void whenMissingContractAndOverrideHasStateCachesOverrideInWriteCache() {
        contractCallContext.setStateOverrides(Map.of(
                Bytes.fromHex(MISSING_CONTRACT_ADDRESS.substring(2)),
                stateOverrideWithState(SLOT_KEY_HEX, OVERRIDE_VALUE_HEX)));

        assertThat(contractStorageReadableKVState.get(MISSING_SLOT_KEY)).isEqualTo(OVERRIDE_SLOT_VALUE);
        org.assertj.core.api.Assertions.assertThat(
                        contractCallContext.getWriteCacheState(ContractStorageReadableKVState.STATE_ID))
                .containsEntry(MISSING_SLOT_KEY, OVERRIDE_SLOT_VALUE);
    }

    @Test
    void whenStateOverrideHasStateDiffCachesOverrideInWriteCache() {
        contractCallContext.setStateOverrides(Map.of(
                Bytes.fromHex(CONTRACT_ID_WITH_NUM_ADDRESS),
                stateOverrideWithStateDiff(SLOT_KEY_HEX, OVERRIDE_VALUE_HEX)));

        assertThat(contractStorageReadableKVState.get(SLOT_KEY)).isEqualTo(OVERRIDE_SLOT_VALUE);
        org.assertj.core.api.Assertions.assertThat(
                        contractCallContext.getWriteCacheState(ContractStorageReadableKVState.STATE_ID))
                .containsEntry(SLOT_KEY, OVERRIDE_SLOT_VALUE);
    }

    @Test
    void whenStateOverrideHasStateDiffReturnsOverrideValue() {
        contractCallContext.setStateOverrides(Map.of(
                Bytes.fromHex(CONTRACT_ID_WITH_NUM_ADDRESS),
                stateOverrideWithStateDiff(SLOT_KEY_HEX, OVERRIDE_VALUE_HEX)));

        assertThat(contractStorageReadableKVState.get(SLOT_KEY)).isEqualTo(OVERRIDE_SLOT_VALUE);
        verify(contractStateService, never()).findStorage(any(), any());
    }

    @Test
    void whenStateOverrideHasStateDiffFallsThroughToDatabaseForUnlistedSlot() {
        contractCallContext.setStateOverrides(Map.of(
                Bytes.fromHex(CONTRACT_ID_WITH_NUM_ADDRESS),
                stateOverrideWithStateDiff(OTHER_SLOT_KEY_HEX, OVERRIDE_VALUE_HEX)));
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(contractStateService.findStorage(ENTITY_ID, BYTES.toByteArray()))
                .thenReturn(Optional.of(BYTES.toByteArray()));

        assertThat(contractStorageReadableKVState.get(SLOT_KEY)).isEqualTo(DATABASE_SLOT_VALUE);
    }

    @Test
    void whenStateOverrideExistsButNoStorageFieldsFallsThroughToDatabase() {
        contractCallContext.setStateOverrides(
                Map.of(Bytes.fromHex(CONTRACT_ID_WITH_NUM_ADDRESS), stateOverrideWithBalance("0x1")));
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(contractStateService.findStorage(ENTITY_ID, BYTES.toByteArray()))
                .thenReturn(Optional.of(BYTES.toByteArray()));

        assertThat(contractStorageReadableKVState.get(SLOT_KEY)).isEqualTo(DATABASE_SLOT_VALUE);
    }

    @Test
    void whenStateOverridesExistButNoMatchingAddressFallsThroughToDatabase() {
        contractCallContext.setStateOverrides(Map.of(
                Bytes.fromHex("000000000000000000000000000000000000dead"),
                stateOverrideWithState(SLOT_KEY_HEX, OVERRIDE_VALUE_HEX)));
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(contractStateService.findStorage(ENTITY_ID, BYTES.toByteArray()))
                .thenReturn(Optional.of(BYTES.toByteArray()));

        assertThat(contractStorageReadableKVState.get(SLOT_KEY)).isEqualTo(DATABASE_SLOT_VALUE);
    }

    @Test
    void testSize() {
        assertThat(contractStorageReadableKVState.size()).isZero();
    }

    private StateOverride stateOverrideWithState(String slotKey, String valueHex) {
        final var entry = new StorageEntry();
        entry.setKey(slotKey);
        entry.setValue(valueHex);
        final var override = new StateOverride();
        override.setState(List.of(entry));
        return override;
    }

    private StateOverride stateOverrideWithStateDiff(String slotKey, String valueHex) {
        final var entry = new StorageEntry();
        entry.setKey(slotKey);
        entry.setValue(valueHex);
        final var override = new StateOverride();
        override.setStateDiff(List.of(entry));
        return override;
    }

    private StateOverride stateOverrideWithBalance(String balanceHex) {
        final var override = new StateOverride();
        override.setBalance(balanceHex);
        return override;
    }

    @Test
    void touchedStorageNotUpdatedWhenContextIsNull() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(contractStateService.findStorage(ENTITY_ID, BYTES.toByteArray()))
                .thenReturn(Optional.of(BYTES.toByteArray()));

        final var result = contractStorageReadableKVState.get(SLOT_KEY);

        assertThat(result).isNotNull();
        assertThat(result).returns(BYTES, SlotValue::value);
        assertThat(contractCallContext.getPrestateContext()).isNull();
    }
}
