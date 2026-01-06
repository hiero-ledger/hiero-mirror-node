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
import java.util.Collections;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.repository.ContractStateRepository;
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
        contextMockedStatic.when(ContractCallContext::isInitialized).thenReturn(true);
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
    void testIterateFromDataSource() {
        assertThat(contractStorageReadableKVState.iterateFromDataSource()).isEqualTo(Collections.emptyIterator());
    }

    @Test
    void testSize() {
        assertThat(contractStorageReadableKVState.size()).isZero();
    }
}
