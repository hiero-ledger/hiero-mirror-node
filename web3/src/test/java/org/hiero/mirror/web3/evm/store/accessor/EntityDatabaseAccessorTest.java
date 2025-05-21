// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store.accessor;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.entityIdNumFromEvmAddress;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.stream.Stream;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.repository.EntityRepository;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityDatabaseAccessorTest {
    private static final String HEX = "0x00000000000000000000000000000000000004e4";
    private static final String ALIAS_HEX = "0x67d8d32e9bf1a9968a5ff53b87d777aa8ebbee69";
    private static final Address ADDRESS = Address.fromHexString(HEX);
    private static final Address ALIAS_ADDRESS = Address.fromHexString(ALIAS_HEX);

    private static final Optional<Long> timestamp = Optional.of(1234L);
    private static final Entity mockEntity = mock(Entity.class);
    private static final long NUM = 1252;

    @InjectMocks
    private EntityDatabaseAccessor entityDatabaseAccessor;

    @Mock
    private EntityRepository entityRepository;

    @Mock
    private CommonProperties commonProperties;

    @Test
    void getEntityByAddress() {
        var commonProperties = CommonProperties.getInstance();
        var entityId = EntityId.of(commonProperties.getShard(), commonProperties.getRealm(), NUM);
        var address = toAddress(entityId);
        when(entityRepository.findByIdAndDeletedIsFalse(entityIdNumFromEvmAddress(address)))
                .thenReturn(Optional.of(mockEntity));
        assertThat(entityDatabaseAccessor.get(address, Optional.empty()))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @Test
    void getEntityByAddressHistorical() {
        var commonProperties = CommonProperties.getInstance();
        var entityId = EntityId.of(commonProperties.getShard(), commonProperties.getRealm(), NUM);
        var address = toAddress(entityId);
        when(entityRepository.findActiveByIdAndTimestamp(entityIdNumFromEvmAddress(address), timestamp.get()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(entityDatabaseAccessor.get(address, timestamp))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void getEntityByAlias(long shard, long realm) {
        when(entityRepository.findByShardAndRealmAndEvmAddressAndDeletedIsFalse(
                        shard, realm, ALIAS_ADDRESS.toArrayUnsafe()))
                .thenReturn(Optional.of(mockEntity));
        when(commonProperties.getShard()).thenReturn(shard);
        when(commonProperties.getRealm()).thenReturn(realm);

        assertThat(entityDatabaseAccessor.get(ALIAS_ADDRESS, Optional.empty()))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void getEntityByAliasHistorical(long shard, long realm) {
        when(entityRepository.findActiveByShardAndRealmAndEvmAddressAndTimestamp(
                        shard, realm, ALIAS_ADDRESS.toArrayUnsafe(), timestamp.get()))
                .thenReturn(Optional.of(mockEntity));
        when(commonProperties.getShard()).thenReturn(shard);
        when(commonProperties.getRealm()).thenReturn(realm);

        assertThat(entityDatabaseAccessor.get(ALIAS_ADDRESS, timestamp))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @Test
    void evmAddressFromIdReturnZeroWhenNoEntityFound() {
        when(entityRepository.findByIdAndDeletedIsFalse(anyLong())).thenReturn(Optional.empty());

        assertThat(entityDatabaseAccessor.evmAddressFromId(mock(EntityId.class), Optional.empty()))
                .isEqualTo(Address.ZERO);
    }

    @Test
    void evmAddressFromIdReturnZeroWhenNoEntityFoundHistorical() {
        when(entityRepository.findActiveByIdAndTimestamp(0L, timestamp.get())).thenReturn(Optional.empty());

        assertThat(entityDatabaseAccessor.evmAddressFromId(mock(EntityId.class), timestamp))
                .isEqualTo(Address.ZERO);
    }

    @Test
    void evmAddressFromIdReturnAddressFromEntityEvmAddressWhenPresent() {
        when(entityRepository.findByIdAndDeletedIsFalse(anyLong())).thenReturn(Optional.of(mockEntity));
        when(mockEntity.getEvmAddress()).thenReturn(ADDRESS.toArray());

        assertThat(entityDatabaseAccessor.evmAddressFromId(mock(EntityId.class), Optional.empty()))
                .isEqualTo(ADDRESS);
    }

    @Test
    void evmAddressFromIdReturnAddressFromEntityEvmAddressWhenPresentHistorical() {
        when(entityRepository.findByIdAndDeletedIsFalse(anyLong())).thenReturn(Optional.of(mockEntity));
        when(mockEntity.getEvmAddress()).thenReturn(ADDRESS.toArray());

        assertThat(entityDatabaseAccessor.evmAddressFromId(mock(EntityId.class), Optional.empty()))
                .isEqualTo(ADDRESS);
    }

    @Test
    void evmAddressFromIdReturnAliasFromEntityWhenPresentAndNoEvmAddress() {
        when(entityRepository.findByIdAndDeletedIsFalse(anyLong())).thenReturn(Optional.of(mockEntity));
        when(mockEntity.getEvmAddress()).thenReturn(null);
        when(mockEntity.getAlias()).thenReturn(ALIAS_ADDRESS.toArray());

        assertThat(entityDatabaseAccessor.evmAddressFromId(mock(EntityId.class), Optional.empty()))
                .isEqualTo(ALIAS_ADDRESS);
    }

    @Test
    void evmAddressFromIdReturnAliasFromEntityWhenPresentAndNoEvmAddressHistorical() {
        when(entityRepository.findActiveByIdAndTimestamp(0L, timestamp.get())).thenReturn(Optional.of(mockEntity));
        when(mockEntity.getEvmAddress()).thenReturn(null);
        when(mockEntity.getAlias()).thenReturn(ALIAS_ADDRESS.toArray());

        assertThat(entityDatabaseAccessor.evmAddressFromId(mock(EntityId.class), timestamp))
                .isEqualTo(ALIAS_ADDRESS);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void evmAddressFromIdReturnToAddressByDefault(long shard, long realm) {
        when(entityRepository.findByIdAndDeletedIsFalse(anyLong())).thenReturn(Optional.of(mockEntity));
        when(mockEntity.getEvmAddress()).thenReturn(null);
        when(mockEntity.getAlias()).thenReturn(null);

        final var entityId = EntityId.of(shard, realm, 3L);
        assertThat(entityDatabaseAccessor.evmAddressFromId(entityId, Optional.empty()))
                .isEqualTo(toAddress(entityId));
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void evmAddressFromIdReturnToAddressByDefaultHistorical(long shard, long realm) {
        final var entityId = EntityId.of(shard, realm, 3L);
        when(entityRepository.findActiveByIdAndTimestamp(entityId.getId(), timestamp.get()))
                .thenReturn(Optional.of(mockEntity));
        when(mockEntity.getEvmAddress()).thenReturn(null);
        when(mockEntity.getAlias()).thenReturn(null);

        assertThat(entityDatabaseAccessor.evmAddressFromId(entityId, timestamp)).isEqualTo(toAddress(entityId));
    }

    // Method that provides the test data
    private static Stream<Arguments> shardAndRealmData() {
        return Stream.of(Arguments.of(0L, 0L), Arguments.of(1L, 2L));
    }
}
