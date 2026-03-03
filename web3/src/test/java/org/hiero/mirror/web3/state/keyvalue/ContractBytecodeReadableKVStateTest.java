// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.entityIdNumFromEvmAddress;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.ContractID.ContractOneOfType;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Collections;
import java.util.Optional;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.repository.ContractRepository;
import org.hiero.mirror.web3.state.CommonEntityAccessor;
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
class ContractBytecodeReadableKVStateTest {

    private static final ContractID CONTRACT_ID_WITH_NUM =
            new ContractID(1L, 0L, new OneOf<>(ContractOneOfType.CONTRACT_NUM, 1L));
    private static final EntityId ENTITY_ID_WITH_NUM = EntityId.of(
            CONTRACT_ID_WITH_NUM.shardNum(), CONTRACT_ID_WITH_NUM.realmNum(), CONTRACT_ID_WITH_NUM.contractNum());
    private static final Bytes BYTES = Bytes.fromBase64("123456");
    private static final Bytecode BYTECODE = new Bytecode(BYTES);
    private static final String HEX = "0x00000000000000000000000000000000000004e4";
    private static final Address MIRROR_ADDRESS = Address.fromHexString(HEX);
    private static final Address EVM_ADDRESS = Address.fromHexString("0xb794f5ea0ba39494ce839613fffba74279579268");
    private static final ContractID CONTRACT_ID_WITH_MIRROR_EVM_ADDRESS = new ContractID(
            1L, 0L, new OneOf<>(ContractOneOfType.EVM_ADDRESS, Bytes.wrap(MIRROR_ADDRESS.toArrayUnsafe())));
    private static final EntityId ENTITY_ID_WITH_MIRROR_EVM_ADDRESS =
            EntityId.of(entityIdNumFromEvmAddress(MIRROR_ADDRESS));
    private static final ContractID CONTRACT_ID_WITH_EVM_ADDRESS =
            new ContractID(1L, 0L, new OneOf<>(ContractOneOfType.EVM_ADDRESS, Bytes.wrap(EVM_ADDRESS.toArrayUnsafe())));
    private static final Entity ENTITY = Entity.builder()
            .evmAddress(EVM_ADDRESS.toArrayUnsafe())
            .shard(1L)
            .realm(0L)
            .num(1L)
            .id(1L)
            .build();

    @InjectMocks
    private ContractBytecodeReadableKVState contractBytecodeReadableKVState;

    private static MockedStatic<ContractCallContext> contextMockedStatic;

    @Mock
    private ContractRepository contractRepository;

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
        contextMockedStatic.when(ContractCallContext::isInitialized).thenReturn(true);
    }

    @Test
    void whenContractIdAndEvmAddressAreNotSetReturnNull() {
        assertThat(contractBytecodeReadableKVState.get(
                        new ContractID(1L, 0L, new OneOf<>(ContractOneOfType.UNSET, null))))
                .satisfies(slotValue -> assertThat(slotValue).isNull());
    }

    @Test
    void whenContractNumIsSetReturnRuntimeBytecode() {
        when(contractRepository.findRuntimeBytecode(ENTITY_ID_WITH_NUM.getId()))
                .thenReturn(Optional.of(BYTES.toByteArray()));
        assertThat(contractBytecodeReadableKVState.get(CONTRACT_ID_WITH_NUM))
                .satisfies(bytecode -> assertThat(bytecode).isEqualTo(BYTECODE));
    }

    @Test
    void whenContractMirrorEvmAddressIsSetReturnRuntimeBytecode() {
        when(contractRepository.findRuntimeBytecode(ENTITY_ID_WITH_MIRROR_EVM_ADDRESS.getId()))
                .thenReturn(Optional.of(BYTES.toByteArray()));
        assertThat(contractBytecodeReadableKVState.get(CONTRACT_ID_WITH_MIRROR_EVM_ADDRESS))
                .satisfies(bytecode -> assertThat(bytecode).isEqualTo(BYTECODE));
    }

    @Test
    void whenContractEvmAddressIsSetReturnRuntimeBytecode() {
        when(commonEntityAccessor.getEntityByEvmAddressAndTimestamp(EVM_ADDRESS.toArray(), Optional.empty()))
                .thenReturn(Optional.of(ENTITY));
        when(contractRepository.findRuntimeBytecode(ENTITY.toEntityId().getId()))
                .thenReturn(Optional.of(BYTES.toByteArray()));
        assertThat(contractBytecodeReadableKVState.get(CONTRACT_ID_WITH_EVM_ADDRESS))
                .satisfies(bytecode -> assertThat(bytecode).isEqualTo(BYTECODE));
    }

    @Test
    void whenContractRuntimeBytecodeIsNullReturnNull() {
        when(contractRepository.findRuntimeBytecode(ENTITY_ID_WITH_NUM.getId())).thenReturn(Optional.empty());
        assertThat(contractBytecodeReadableKVState.get(CONTRACT_ID_WITH_NUM))
                .satisfies(bytecode -> assertThat(bytecode).isNull());
    }

    @Test
    void getExpectedSize() {
        assertThat(contractBytecodeReadableKVState.size()).isZero();
    }

    @Test
    void iterateFromDataSourceReturnsEmptyIterator() {
        assertThat(contractBytecodeReadableKVState.iterateFromDataSource()).isEqualTo(Collections.emptyIterator());
    }
}
