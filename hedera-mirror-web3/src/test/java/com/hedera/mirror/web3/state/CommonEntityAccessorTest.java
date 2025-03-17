// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state;

import static com.hedera.services.utils.EntityIdUtils.entityIdFromId;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.AccountID.AccountOneOfType;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.repository.EntityRepository;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.store.models.Id;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommonEntityAccessorTest {
    private static final String EVM_ADDRESS_HEX = "0x67d8d32e9bf1a9968a5ff53b87d777aa8ebbee69";
    private static final Address EVM_ADDRESS = Address.fromHexString(EVM_ADDRESS_HEX);
    private static final AccountID ACCOUNT_ALIAS_WITH_EVM_ADDRESS =
            new AccountID(0L, 0L, new OneOf<>(AccountOneOfType.ALIAS, Bytes.wrap(EVM_ADDRESS.toArray())));
    private static final String ALIAS_HEX = "3a2102b3c641418e89452cd5202adfd4758f459acb8e364f741fd16cd2db79835d39d2";
    private static final AccountID ACCOUNT_ALIAS_WITH_KEY =
            new AccountID(0L, 0L, new OneOf<>(AccountOneOfType.ALIAS, Bytes.wrap(ALIAS_HEX.getBytes())));
    private static final Long NUM = 1252L;
    private static final AccountID ACCOUNT_ID = new AccountID(0L, 1L, new OneOf<>(AccountOneOfType.ACCOUNT_NUM, NUM));
    private static final Optional<Long> timestamp = Optional.of(1234L);
    private static final Entity mockEntity = mock(Entity.class);

    @InjectMocks
    private CommonEntityAccessor commonEntityAccessor;

    @Mock
    private EntityRepository entityRepository;

    @Mock
    private CommonProperties commonProperties;

    @Test
    void getEntityByAddress() {
        final var id = new Id(0L, 1L, NUM);
        when(entityRepository.findByIdAndDeletedIsFalse(entityIdFromId(id).getId()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(ACCOUNT_ID, Optional.empty()))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @Test
    void getEntityByAddressHistorical() {
        final var id = new Id(0L, 1L, NUM);
        when(entityRepository.findActiveByIdAndTimestamp(entityIdFromId(id).getId(), timestamp.get()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(ACCOUNT_ID, timestamp))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @Test
    void getEntityByTokenID() {
        final var tokenID = new TokenID(0L, 1L, NUM);
        final var entityId = EntityId.of(tokenID.shardNum(), tokenID.realmNum(), tokenID.tokenNum());

        when(entityRepository.findByIdAndDeletedIsFalse(entityId.getId())).thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(tokenID, Optional.empty()))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @Test
    void getEntityByTokenIDHistorical() {
        final var tokenID = new TokenID(0L, 1L, NUM);
        final var entityId = EntityId.of(tokenID.shardNum(), tokenID.realmNum(), tokenID.tokenNum());

        when(entityRepository.findActiveByIdAndTimestamp(entityId.getId(), timestamp.get()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(tokenID, timestamp))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @Test
    void getEntityByEvmAddress() {
        when(entityRepository.findByEvmAddressOrAlias(
                        ACCOUNT_ALIAS_WITH_EVM_ADDRESS.alias().toByteArray(), ACCOUNT_ALIAS_WITH_EVM_ADDRESS.shardNum(), ACCOUNT_ALIAS_WITH_EVM_ADDRESS.realmNum()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(ACCOUNT_ALIAS_WITH_EVM_ADDRESS, Optional.empty()))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @Test
    void getEntityByEvmAddressHistorical() {
        when(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        ACCOUNT_ALIAS_WITH_EVM_ADDRESS.alias().toByteArray(), timestamp.get()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(ACCOUNT_ALIAS_WITH_EVM_ADDRESS, timestamp))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @Test
    void getEntityByAlias() {
        when(entityRepository.findByEvmAddressOrAlias(
                        ACCOUNT_ALIAS_WITH_KEY.alias().toByteArray(), ACCOUNT_ALIAS_WITH_EVM_ADDRESS.shardNum(), ACCOUNT_ALIAS_WITH_EVM_ADDRESS.realmNum()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(ACCOUNT_ALIAS_WITH_KEY, Optional.empty()))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @Test
    void getEntityByAliasHistorical() {
        when(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        ACCOUNT_ALIAS_WITH_KEY.alias().toByteArray(), timestamp.get()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(ACCOUNT_ALIAS_WITH_KEY, timestamp))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @Test
    void getEntityByEvmAddressOrAliasAndTimestampWithEvmAddress() {
        when(entityRepository.findByEvmAddressOrAlias(
                        ACCOUNT_ALIAS_WITH_EVM_ADDRESS.alias().toByteArray(), ACCOUNT_ALIAS_WITH_EVM_ADDRESS.shardNum(), ACCOUNT_ALIAS_WITH_EVM_ADDRESS.realmNum()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(ACCOUNT_ALIAS_WITH_EVM_ADDRESS.alias(), Optional.empty()))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @Test
    void getEntityByEvmAddressOrAliasAndTimestampWithEvmAddressHistorical() {
        when(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        ACCOUNT_ALIAS_WITH_EVM_ADDRESS.alias().toByteArray(), timestamp.get()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(ACCOUNT_ALIAS_WITH_EVM_ADDRESS.alias(), timestamp))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @Test
    void getEntityByEvmAddressOrAliasAndTimestampWithKey() {
        when(entityRepository.findByEvmAddressOrAlias(
                        ACCOUNT_ALIAS_WITH_KEY.alias().toByteArray(), ACCOUNT_ALIAS_WITH_EVM_ADDRESS.shardNum(), ACCOUNT_ALIAS_WITH_EVM_ADDRESS.realmNum()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(ACCOUNT_ALIAS_WITH_KEY.alias(), Optional.empty()))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @Test
    void getEntityByEvmAddressOrAliasAndTimestampWithKeyHistorical() {
        when(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        ACCOUNT_ALIAS_WITH_KEY.alias().toByteArray(), timestamp.get()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(ACCOUNT_ALIAS_WITH_KEY.alias(), timestamp))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @Test
    void getEntityByEvmAddressAndTimestamp() {
        when(entityRepository.findByEvmAddressAndDeletedIsFalse(
                        ACCOUNT_ALIAS_WITH_EVM_ADDRESS.alias().toByteArray()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.getEntityByEvmAddressAndTimestamp(
                        ACCOUNT_ALIAS_WITH_EVM_ADDRESS.alias().toByteArray(), Optional.empty()))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @Test
    void getEntityByEvmAddressAndTimestampHistorical() {
        when(entityRepository.findActiveByEvmAddressAndTimestamp(
                        ACCOUNT_ALIAS_WITH_EVM_ADDRESS.alias().toByteArray(), timestamp.get(), ACCOUNT_ALIAS_WITH_EVM_ADDRESS.shardNum(), ACCOUNT_ALIAS_WITH_EVM_ADDRESS.realmNum()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.getEntityByEvmAddressAndTimestamp(
                        ACCOUNT_ALIAS_WITH_EVM_ADDRESS.alias().toByteArray(), timestamp))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }
}
