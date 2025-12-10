// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.AccountID.AccountOneOfType;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Optional;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.repository.EntityRepository;
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
    private static final String ALIAS_HEX = "3a2102b3c641418e89452cd5202adfd4758f459acb8e364f741fd16cd2db79835d39d2";
    private static final Long NUM = 1252L;
    private static final Optional<Long> timestamp = Optional.of(1234L);
    private static final Entity mockEntity = mock(Entity.class);
    private static final CommonProperties COMMON_PROPERTIES = CommonProperties.getInstance();

    @InjectMocks
    private CommonEntityAccessor commonEntityAccessor;

    @Mock
    private EntityRepository entityRepository;

    @Test
    void getEntityByAddress() {
        var accountId = new AccountID(
                COMMON_PROPERTIES.getShard(),
                COMMON_PROPERTIES.getRealm(),
                new OneOf<>(AccountOneOfType.ACCOUNT_NUM, NUM));
        when(entityRepository.findByIdAndDeletedIsFalse(
                        EntityId.of(COMMON_PROPERTIES.getShard(), COMMON_PROPERTIES.getRealm(), NUM)
                                .getId()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(accountId, Optional.empty()))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @Test
    void getEntityByAddressHistorical() {
        var accountId = new AccountID(
                COMMON_PROPERTIES.getShard(),
                COMMON_PROPERTIES.getRealm(),
                new OneOf<>(AccountOneOfType.ACCOUNT_NUM, NUM));
        when(entityRepository.findActiveByIdAndTimestamp(
                        EntityId.of(COMMON_PROPERTIES.getShard(), COMMON_PROPERTIES.getRealm(), NUM)
                                .getId(),
                        timestamp.get()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(accountId, timestamp))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @Test
    void getEntityByTokenID() {
        final var tokenID = new TokenID(COMMON_PROPERTIES.getShard(), COMMON_PROPERTIES.getRealm(), NUM);
        final var entityId = EntityId.of(tokenID.shardNum(), tokenID.realmNum(), tokenID.tokenNum());

        when(entityRepository.findByIdAndDeletedIsFalse(entityId.getId())).thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(tokenID, Optional.empty()))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @Test
    void getEntityByTokenIDHistorical() {
        final var tokenID = new TokenID(COMMON_PROPERTIES.getShard(), COMMON_PROPERTIES.getRealm(), NUM);
        final var entityId = EntityId.of(tokenID.shardNum(), tokenID.realmNum(), tokenID.tokenNum());

        when(entityRepository.findActiveByIdAndTimestamp(entityId.getId(), timestamp.get()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(tokenID, timestamp))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @Test
    void getEntityByEvmAddress() {
        var account = createAccountAliasWithEVMAddress();
        when(entityRepository.findByEvmAddressOrAliasAndDeletedIsFalse(
                        account.alias().toByteArray()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(account, Optional.empty()))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @Test
    void getEntityByEvmAddressHistorical() {
        var account = createAccountAliasWithEVMAddress();
        when(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        account.alias().toByteArray(), timestamp.get()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(account, timestamp))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @Test
    void getEntityByAlias() {
        var account = createAccountAliasWithKey();
        when(entityRepository.findByEvmAddressOrAliasAndDeletedIsFalse(
                        account.alias().toByteArray()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(account, Optional.empty()))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @Test
    void getEntityByAliasHistorical() {
        var account = createAccountAliasWithKey();
        when(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        account.alias().toByteArray(), timestamp.get()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(account, timestamp))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @Test
    void getEntityByEvmAddressOrAliasAndTimestampWithEvmAddress() {
        var account = createAccountAliasWithEVMAddress();
        when(entityRepository.findByEvmAddressOrAliasAndDeletedIsFalse(
                        account.alias().toByteArray()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(account.alias(), Optional.empty()))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @Test
    void getEntityByEvmAddressOrAliasAndTimestampWithEvmAddressHistorical() {
        var account = createAccountAliasWithEVMAddress();
        when(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        account.alias().toByteArray(), timestamp.get()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(account.alias(), timestamp))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @Test
    void getEntityByEvmAddressOrAliasAndTimestampWithKey() {
        var account = createAccountAliasWithKey();
        when(entityRepository.findByEvmAddressOrAliasAndDeletedIsFalse(
                        account.alias().toByteArray()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(account.alias(), Optional.empty()))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @Test
    void getEntityByEvmAddressOrAliasAndTimestampWithKeyHistorical() {
        var account = createAccountAliasWithKey();
        when(entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(
                        account.alias().toByteArray(), timestamp.get()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(account.alias(), timestamp))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @Test
    void getEntityByEvmAddressAndTimestamp() {
        var account = createAccountAliasWithEVMAddress();
        when(entityRepository.findByEvmAddressAndDeletedIsFalse(account.alias().toByteArray()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.getEntityByEvmAddressAndTimestamp(
                        account.alias().toByteArray(), Optional.empty()))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @Test
    void getEntityByEvmAddressAndTimestampHistorical() {
        var account = createAccountAliasWithEVMAddress();
        when(entityRepository.findActiveByEvmAddressAndTimestamp(account.alias().toByteArray(), timestamp.get()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.getEntityByEvmAddressAndTimestamp(
                        account.alias().toByteArray(), timestamp))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    private AccountID createAccountAliasWithEVMAddress() {
        return new AccountID(
                COMMON_PROPERTIES.getShard(),
                COMMON_PROPERTIES.getRealm(),
                new OneOf<>(AccountOneOfType.ALIAS, Bytes.wrap(EVM_ADDRESS.toArray())));
    }

    private AccountID createAccountAliasWithKey() {
        return new AccountID(
                COMMON_PROPERTIES.getShard(),
                COMMON_PROPERTIES.getRealm(),
                new OneOf<>(AccountOneOfType.ALIAS, Bytes.wrap(ALIAS_HEX.getBytes())));
    }
}
