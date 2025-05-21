// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import static com.hedera.services.utils.EntityIdUtils.entityIdFromId;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.AccountID.AccountOneOfType;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.store.models.Id;
import java.util.Optional;
import java.util.stream.Stream;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.repository.EntityRepository;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

    @InjectMocks
    private CommonEntityAccessor commonEntityAccessor;

    @Mock
    private EntityRepository entityRepository;

    @Mock
    private CommonProperties commonProperties;

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void getEntityByAddress(long shard, long realm) {
        final var id = new Id(shard, realm, NUM);
        var accountId = new AccountID(shard, realm, new OneOf<>(AccountOneOfType.ACCOUNT_NUM, NUM));
        when(entityRepository.findByIdAndDeletedIsFalse(entityIdFromId(id).getId()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(accountId, Optional.empty()))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void getEntityByAddressHistorical(long shard, long realm) {
        final var id = new Id(shard, realm, NUM);
        var accountId = new AccountID(shard, realm, new OneOf<>(AccountOneOfType.ACCOUNT_NUM, NUM));
        when(entityRepository.findActiveByIdAndTimestamp(entityIdFromId(id).getId(), timestamp.get()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(accountId, timestamp))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void getEntityByTokenID(long shard, long realm) {
        final var tokenID = new TokenID(shard, realm, NUM);
        final var entityId = EntityId.of(tokenID.shardNum(), tokenID.realmNum(), tokenID.tokenNum());

        when(entityRepository.findByIdAndDeletedIsFalse(entityId.getId())).thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(tokenID, Optional.empty()))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void getEntityByTokenIDHistorical(long shard, long realm) {
        final var tokenID = new TokenID(shard, realm, NUM);
        final var entityId = EntityId.of(tokenID.shardNum(), tokenID.realmNum(), tokenID.tokenNum());

        when(entityRepository.findActiveByIdAndTimestamp(entityId.getId(), timestamp.get()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(tokenID, timestamp))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void getEntityByEvmAddress(long shard, long realm) {
        var account = createAccountAliasWithEVMAddress(shard, realm);
        when(commonProperties.getShard()).thenReturn(shard);
        when(commonProperties.getRealm()).thenReturn(realm);
        when(entityRepository.findByShardAndRealmAndEvmAddressOrAliasAndDeletedIsFalse(
                        account.shardNum(), account.realmNum(), account.alias().toByteArray()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(account, Optional.empty()))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void getEntityByEvmAddressHistorical(long shard, long realm) {
        var account = createAccountAliasWithEVMAddress(shard, realm);
        when(commonProperties.getShard()).thenReturn(shard);
        when(commonProperties.getRealm()).thenReturn(realm);
        when(entityRepository.findActiveByShardAndRealmAndEvmAddressOrAliasAndTimestamp(
                        account.shardNum(), account.realmNum(), account.alias().toByteArray(), timestamp.get()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(account, timestamp))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void getEntityByAlias(long shard, long realm) {
        var account = createAccountAliasWithKey(shard, realm);
        when(commonProperties.getShard()).thenReturn(shard);
        when(commonProperties.getRealm()).thenReturn(realm);
        when(entityRepository.findByShardAndRealmAndEvmAddressOrAliasAndDeletedIsFalse(
                        account.shardNum(), account.realmNum(), account.alias().toByteArray()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(account, Optional.empty()))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void getEntityByAliasHistorical(long shard, long realm) {
        var account = createAccountAliasWithKey(shard, realm);
        when(commonProperties.getShard()).thenReturn(shard);
        when(commonProperties.getRealm()).thenReturn(realm);
        when(entityRepository.findActiveByShardAndRealmAndEvmAddressOrAliasAndTimestamp(
                        account.shardNum(), account.realmNum(), account.alias().toByteArray(), timestamp.get()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(account, timestamp))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void getEntityByEvmAddressOrAliasAndTimestampWithEvmAddress(long shard, long realm) {
        var account = createAccountAliasWithEVMAddress(shard, realm);
        when(commonProperties.getShard()).thenReturn(shard);
        when(commonProperties.getRealm()).thenReturn(realm);
        when(entityRepository.findByShardAndRealmAndEvmAddressOrAliasAndDeletedIsFalse(
                        account.shardNum(), account.realmNum(), account.alias().toByteArray()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(account.alias(), Optional.empty()))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void getEntityByEvmAddressOrAliasAndTimestampWithEvmAddressHistorical(long shard, long realm) {
        var account = createAccountAliasWithEVMAddress(shard, realm);
        when(commonProperties.getShard()).thenReturn(shard);
        when(commonProperties.getRealm()).thenReturn(realm);
        when(entityRepository.findActiveByShardAndRealmAndEvmAddressOrAliasAndTimestamp(
                        account.shardNum(), account.realmNum(), account.alias().toByteArray(), timestamp.get()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(account.alias(), timestamp))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void getEntityByEvmAddressOrAliasAndTimestampWithKey(long shard, long realm) {
        var account = createAccountAliasWithKey(shard, realm);
        when(commonProperties.getShard()).thenReturn(shard);
        when(commonProperties.getRealm()).thenReturn(realm);
        when(entityRepository.findByShardAndRealmAndEvmAddressOrAliasAndDeletedIsFalse(
                        account.shardNum(), account.realmNum(), account.alias().toByteArray()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(account.alias(), Optional.empty()))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void getEntityByEvmAddressOrAliasAndTimestampWithKeyHistorical(long shard, long realm) {
        var account = createAccountAliasWithKey(shard, realm);
        when(commonProperties.getShard()).thenReturn(shard);
        when(commonProperties.getRealm()).thenReturn(realm);
        when(entityRepository.findActiveByShardAndRealmAndEvmAddressOrAliasAndTimestamp(
                        account.shardNum(), account.realmNum(), account.alias().toByteArray(), timestamp.get()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.get(account.alias(), timestamp))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void getEntityByEvmAddressAndTimestamp(long shard, long realm) {
        var account = createAccountAliasWithEVMAddress(shard, realm);
        when(commonProperties.getShard()).thenReturn(shard);
        when(commonProperties.getRealm()).thenReturn(realm);
        when(entityRepository.findByShardAndRealmAndEvmAddressAndDeletedIsFalse(
                        account.shardNum(), account.realmNum(), account.alias().toByteArray()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.getEntityByEvmAddressAndTimestamp(
                        account.alias().toByteArray(), Optional.empty()))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void getEntityByEvmAddressAndTimestampHistorical(long shard, long realm) {
        var account = createAccountAliasWithEVMAddress(shard, realm);
        when(commonProperties.getShard()).thenReturn(shard);
        when(commonProperties.getRealm()).thenReturn(realm);
        when(entityRepository.findActiveByShardAndRealmAndEvmAddressAndTimestamp(
                        account.shardNum(), account.realmNum(), account.alias().toByteArray(), timestamp.get()))
                .thenReturn(Optional.of(mockEntity));

        assertThat(commonEntityAccessor.getEntityByEvmAddressAndTimestamp(
                        account.alias().toByteArray(), timestamp))
                .hasValueSatisfying(entity -> assertThat(entity).isEqualTo(mockEntity));
    }

    // Method that provides the test data
    private static Stream<Arguments> shardAndRealmData() {
        return Stream.of(Arguments.of(0L, 0L), Arguments.of(1L, 2L));
    }

    private AccountID createAccountAliasWithEVMAddress(long shard, long realm) {
        return new AccountID(shard, realm, new OneOf<>(AccountOneOfType.ALIAS, Bytes.wrap(EVM_ADDRESS.toArray())));
    }

    private AccountID createAccountAliasWithKey(long shard, long realm) {
        return new AccountID(shard, realm, new OneOf<>(AccountOneOfType.ALIAS, Bytes.wrap(ALIAS_HEX.getBytes())));
    }
}
