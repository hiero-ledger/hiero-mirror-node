/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.web3.state.keyvalue;

import static com.hedera.mirror.web3.state.Utils.padToBytes32;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.ContractID.ContractOneOfType;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.repository.ContractStateRepository;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Collections;
import java.util.Optional;
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
    private static final Bytes BYTES = Bytes.wrap(padToBytes32("123456".getBytes()));
    private static final SlotKey SLOT_KEY = new SlotKey(CONTRACT_ID, BYTES);
    private static final EntityId ENTITY_ID =
            EntityId.of(CONTRACT_ID.shardNum(), CONTRACT_ID.realmNum(), CONTRACT_ID.contractNum());
    private static MockedStatic<ContractCallContext> contextMockedStatic;

    @InjectMocks
    private ContractStorageReadableKVState contractStorageReadableKVState;

    @Mock
    private ContractStateRepository contractStateRepository;

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
        when(contractStateRepository.findStorage(ENTITY_ID.getId(), BYTES.toByteArray()))
                .thenReturn(Optional.of(BYTES.toByteArray()));
        assertThat(contractStorageReadableKVState.get(SLOT_KEY))
                .satisfies(slotValue -> assertThat(slotValue).returns(BYTES, SlotValue::value));
    }

    @Test
    void whenTimestampIsNotNullReturnsHistoricalSlot() {
        final var blockTimestamp = 1234567L;
        when(contractCallContext.getTimestamp()).thenReturn(Optional.of(blockTimestamp));
        when(contractStateRepository.findStorageByBlockTimestamp(
                        ENTITY_ID.getId(), BYTES.toByteArray(), blockTimestamp))
                .thenReturn(Optional.of(BYTES.toByteArray()));
        assertThat(contractStorageReadableKVState.get(SLOT_KEY))
                .satisfies(slotValue -> assertThat(slotValue).returns(BYTES, SlotValue::value));
    }

    @Test
    void whenSlotNotFoundReturnsNullForLatestBlock() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(contractStateRepository.findStorage(anyLong(), any())).thenReturn(Optional.empty());
        assertThat(contractStorageReadableKVState.get(SLOT_KEY))
                .satisfies(slotValue -> assertThat(slotValue).isNull());
    }

    @Test
    void whenSlotNotFoundReturnsNullForHistoricalBlock() {
        final var blockTimestamp = 1234567L;
        when(contractCallContext.getTimestamp()).thenReturn(Optional.of(blockTimestamp));
        when(contractStateRepository.findStorageByBlockTimestamp(anyLong(), any(), anyLong()))
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
