// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.leftPadBytes;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.ContractID.ContractOneOfType;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.PrestateContext;
import org.hiero.mirror.web3.service.ContractStateService;
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
    private static MockedStatic<ContractCallContext> contextMockedStatic;

    @InjectMocks
    private ContractStorageReadableKVState contractStorageReadableKVState;

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

    @Test
    void touchedStorageIsUpdated() {
        final var prestateContext =
                PrestateContext.builder().storage(true).code(false).diff(false).build();
        contractCallContext.setPrestateContext(prestateContext);
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(contractStateService.findStorage(ENTITY_ID, BYTES.toByteArray()))
                .thenReturn(Optional.of(BYTES.toByteArray()));

        contractStorageReadableKVState.get(SLOT_KEY);

        final var touchedKeys = prestateContext.getTouchedStorageKeys();
        assertThat(touchedKeys).isNotNull();
        assertThat(touchedKeys.isEmpty()).isFalse();
        final var contractKeys = touchedKeys.entrySet().iterator().next();
        assertThat(contractKeys.getValue()).isNotNull();
        assertThat(contractKeys.getValue().contains(SLOT_KEY.key().toHex())).isTrue();
    }

    @Test
    void multipleSlotKeysAreTrackedSuccessfully() {
        final var prestateContext =
                PrestateContext.builder().storage(true).code(false).diff(false).build();
        contractCallContext.setPrestateContext(prestateContext);
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(contractStateService.findStorage(any(), any())).thenReturn(Optional.of(BYTES.toByteArray()));

        final var secondKeyBytes = Bytes.wrap(leftPadBytes("789012".getBytes(), Bytes32.SIZE));
        final var secondSlotKey = new SlotKey(CONTRACT_ID, secondKeyBytes);

        contractStorageReadableKVState.get(SLOT_KEY);
        contractStorageReadableKVState.get(secondSlotKey);

        final var touchedKeys = prestateContext.getTouchedStorageKeys();
        assertThat(touchedKeys.isEmpty()).isFalse();
        final var contractSlots = touchedKeys.entrySet().iterator().next().getValue();
        assertThat(contractSlots.contains(SLOT_KEY.key().toHex())).isTrue();
        assertThat(contractSlots.contains(secondKeyBytes.toHex())).isTrue();
        assertThat(contractSlots.size()).isEqualTo(2);
    }

    @Test
    void differentContractsAreTrackedSuccessfully() {
        final var prestateContext =
                PrestateContext.builder().storage(true).code(false).diff(false).build();
        contractCallContext.setPrestateContext(prestateContext);
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(contractStateService.findStorage(any(), any())).thenReturn(Optional.of(BYTES.toByteArray()));

        final var secondContractId = new ContractID(1L, 0L, new OneOf<>(ContractOneOfType.CONTRACT_NUM, 2L));
        final var secondSlotKey = new SlotKey(secondContractId, BYTES);

        contractStorageReadableKVState.get(SLOT_KEY);
        contractStorageReadableKVState.get(secondSlotKey);

        final var touchedKeys = prestateContext.getTouchedStorageKeys();
        assertThat(touchedKeys.size()).isEqualTo(2);
    }

    @Test
    void touchedStorageIsUpdatedForContractWithEvmAddress() {
        final var prestateContext =
                PrestateContext.builder().storage(true).code(false).diff(false).build();
        contractCallContext.setPrestateContext(prestateContext);
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());

        final var evmAddressBytes = Bytes.wrap(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1});
        final var evmContractId = new ContractID(0L, 0L, new OneOf<>(ContractOneOfType.EVM_ADDRESS, evmAddressBytes));
        final var evmSlotKey = new SlotKey(evmContractId, BYTES);
        final var evmEntityId = EntityId.of(0L, 0L, 1L);

        when(contractStateService.findStorage(evmEntityId, BYTES.toByteArray()))
                .thenReturn(Optional.of(BYTES.toByteArray()));

        contractStorageReadableKVState.get(evmSlotKey);

        final var touchedKeys = prestateContext.getTouchedStorageKeys();
        assertThat(touchedKeys.isEmpty()).isFalse();
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

    @Test
    void noTrackingForContractIdWithoutNumOrEvmAddress() {
        final var prestateContext =
                PrestateContext.builder().storage(true).code(false).diff(false).build();
        contractCallContext.setPrestateContext(prestateContext);
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());

        final var emptyContractId = new ContractID(0L, 0L, new OneOf<>(ContractOneOfType.UNSET, null));
        final var emptySlotKey = new SlotKey(emptyContractId, BYTES);
        when(contractStateService.findStorage(any(), any())).thenReturn(Optional.of(BYTES.toByteArray()));

        contractStorageReadableKVState.get(emptySlotKey);

        assertThat(prestateContext.getTouchedStorageKeys().isEmpty()).isTrue();
    }

    @Test
    void duplicateSlotKeyIsTrackedOnce() {
        final var prestateContext =
                PrestateContext.builder().storage(true).code(false).diff(false).build();
        contractCallContext.setPrestateContext(prestateContext);
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(contractStateService.findStorage(ENTITY_ID, BYTES.toByteArray()))
                .thenReturn(Optional.of(BYTES.toByteArray()));

        contractStorageReadableKVState.get(SLOT_KEY);
        contractStorageReadableKVState.get(SLOT_KEY);

        final var touchedKeys = prestateContext.getTouchedStorageKeys();
        assertThat(touchedKeys.isEmpty()).isFalse();
        final Set<String> contractSlots =
                touchedKeys.entrySet().iterator().next().getValue();
        assertThat(contractSlots.size()).isEqualTo(1);
        assertThat(contractSlots.contains(SLOT_KEY.key().toHex())).isTrue();
    }

    @Test
    void slotKeyWithNoContractIdIsNotTracked() {
        final var prestateContext =
                PrestateContext.builder().storage(true).code(false).diff(false).build();
        contractCallContext.setPrestateContext(prestateContext);

        final var result = contractStorageReadableKVState.get(new SlotKey(null, BYTES));

        assertThat(result).isNull();
        assertThat(prestateContext.getTouchedStorageKeys().isEmpty()).isTrue();
    }
}
