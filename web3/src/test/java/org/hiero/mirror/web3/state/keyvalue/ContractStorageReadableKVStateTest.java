// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.leftPadBytes;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.ContractID.ContractOneOfType;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.repository.ContractStateRepository;
import org.hiero.mirror.web3.service.ContractStateService;
import org.hiero.mirror.web3.viewmodel.StateOverride;
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
    private static final Bytes BYTES = Bytes.wrap(leftPadBytes("123456".getBytes(), Bytes32.SIZE));
    private static final SlotKey SLOT_KEY = new SlotKey(CONTRACT_ID, BYTES);
    private static final EntityId ENTITY_ID =
            EntityId.of(CONTRACT_ID.shardNum(), CONTRACT_ID.realmNum(), CONTRACT_ID.contractNum());
    // State-override test fixtures
    private static final String CONTRACT_EVM_ADDR = "0x0000000000000000000000000000000000000001";
    private static final Bytes OVERRIDE_SLOT_BYTES = Bytes.wrap(leftPadBytes(new byte[] {1}, Bytes32.SIZE));
    private static final SlotKey OVERRIDE_SLOT_KEY = new SlotKey(CONTRACT_ID, OVERRIDE_SLOT_BYTES);
    // Slot key after normalisation: 32-byte left-padded hex, no 0x prefix
    private static final String SLOT_NORMALIZED_HEX =
            "0000000000000000000000000000000000000000000000000000000000000001";
    private static final String VALUE_HEX = "0x64"; // 100 decimal
    private static final Bytes EXPECTED_VALUE_BYTES = Bytes.wrap(leftPadBytes(new byte[] {100}, Bytes32.SIZE));
    private static MockedStatic<ContractCallContext> contextMockedStatic;

    @InjectMocks
    private ContractStorageReadableKVState contractStorageReadableKVState;

    @Mock
    private ContractStateRepository contractStateRepository;

    @Mock
    private ContractStateService contractStateService;

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
    void testSize() {
        assertThat(contractStorageReadableKVState.size()).isZero();
    }

    // ── State override tests ──────────────────────────────────────────────────

    @Test
    void stateOverrideReturnsOverriddenValueForListedSlot() {
        final var override = new StateOverride();
        override.setState(Map.of(SLOT_NORMALIZED_HEX, VALUE_HEX));
        doReturn(Map.of(CONTRACT_EVM_ADDR, override)).when(contractCallContext).getStateOverrides();

        assertThat(contractStorageReadableKVState.get(OVERRIDE_SLOT_KEY))
                .satisfies(sv -> assertThat(sv).returns(EXPECTED_VALUE_BYTES, SlotValue::value));
    }

    @Test
    void stateOverrideReturnsNullForSlotNotInOverride() {
        final var override = new StateOverride();
        override.setState(Map.of()); // full replacement with empty map — no slots exist
        doReturn(Map.of(CONTRACT_EVM_ADDR, override)).when(contractCallContext).getStateOverrides();

        assertThat(contractStorageReadableKVState.get(OVERRIDE_SLOT_KEY)).isNull();
    }

    @Test
    void stateDiffOverrideReturnsOverriddenValueForListedSlot() {
        final var override = new StateOverride();
        override.setStateDiff(Map.of(SLOT_NORMALIZED_HEX, VALUE_HEX));
        doReturn(Map.of(CONTRACT_EVM_ADDR, override)).when(contractCallContext).getStateOverrides();

        assertThat(contractStorageReadableKVState.get(OVERRIDE_SLOT_KEY))
                .satisfies(sv -> assertThat(sv).returns(EXPECTED_VALUE_BYTES, SlotValue::value));
    }

    @Test
    void stateDiffOverrideFallsThroughToDbForUnlistedSlot() {
        final var override = new StateOverride();
        override.setStateDiff(Map.of()); // listed slots are empty — unlisted fall through to DB
        doReturn(Map.of(CONTRACT_EVM_ADDR, override)).when(contractCallContext).getStateOverrides();
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(contractStateService.findStorage(ENTITY_ID, OVERRIDE_SLOT_BYTES.toByteArray()))
                .thenReturn(Optional.of(BYTES.toByteArray()));

        assertThat(contractStorageReadableKVState.get(OVERRIDE_SLOT_KEY)).isNotNull();
    }
}
