// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.toEvmAddress;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.service.evm.utils.EthSigsUtils;
import com.hedera.services.jproto.JKey;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.Key;
import java.security.InvalidKeyException;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.ContextExtension;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.Store.OnMissing;
import org.hiero.mirror.web3.evm.utils.EvmTokenUtils;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(ContextExtension.class)
@ExtendWith(MockitoExtension.class)
class MirrorEvmContractAliasesTest {

    private static final String HEX = "0x00000000000000000000000000000000000004e4";
    private static final Address ADDRESS = Address.fromHexString(HEX);

    private static final String ALIAS_HEX = "0xabcdefabcdefabcdefbabcdefabcdefabcdefbbb";
    private static final Address ALIAS = Address.fromHexString(ALIAS_HEX);

    private static final EntityId entityId = EntityId.of(0L, 0L, 1252L);
    private static final Id id = new Id(0L, 0L, 1252L);

    // Method that provides the test data
    public static Stream<Arguments> shardAndRealmData() {
        return Stream.of(Arguments.of(0L, 0L), Arguments.of(1L, 2L));
    }

    @Mock
    private Store store;

    @Mock
    private Account account;

    private final CommonProperties commonProperties = CommonProperties.getInstance();

    private MirrorEvmContractAliases mirrorEvmContractAliases;

    @BeforeEach
    void setup() {
        mirrorEvmContractAliases = new MirrorEvmContractAliases(store);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void resolveForEvmShouldReturnInputWhenItIsMirrorAddress(final long shard, final long realm) {
        final var entityId = EntityId.of(shard, realm, 1252L);
        final var address = EvmTokenUtils.toAddress(entityId);
        commonProperties.setShard(shard);
        commonProperties.setRealm(realm);

        assertThat(mirrorEvmContractAliases.resolveForEvm(address)).isEqualTo(address);
    }

    @Test
    void resolveForEvmWhenAliasIsPresentShouldReturnMatchingAddress() {
        given(store.getAccount(ALIAS, OnMissing.DONT_THROW)).willReturn(account);
        given(account.getId()).willReturn(id);
        assertThat(mirrorEvmContractAliases.resolveForEvm(ALIAS)).isEqualTo(ADDRESS);
    }

    @Test
    void resolveForEvmWhenAliasAndPendingAliasIsPresentShouldReturnMatchingAddressFromPendingAliases() {
        given(store.getAccount(ALIAS, OnMissing.DONT_THROW)).willReturn(account);
        given(account.getId()).willReturn(id);
        assertThat(mirrorEvmContractAliases.resolveForEvm(ALIAS)).isEqualTo(ADDRESS);
    }

    @Test
    void resolveForEvmForAccountWhenAliasesNotPresentShouldReturnEntityEvmAddress() {
        when(store.getAccount(ALIAS, OnMissing.DONT_THROW)).thenReturn(account);
        when(account.getId()).thenReturn(id);

        assertThat(Bytes.wrap(mirrorEvmContractAliases.resolveForEvm(ALIAS).toArray()))
                .isEqualTo(Bytes.wrap(toEvmAddress(entityId)));
    }

    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9})
    @ParameterizedTest
    void isPrecompileAddressShouldReturnTrue(int address) {
        assertThat(mirrorEvmContractAliases.isNativePrecompileAddress(Address.precompiled(address)))
                .isTrue();
    }

    @ValueSource(ints = {0, 10, 100})
    @ParameterizedTest
    void isPrecompileAddressShouldReturnFalse(int address) {
        assertThat(mirrorEvmContractAliases.isNativePrecompileAddress(Address.precompiled(address)))
                .isFalse();
        assertThat(mirrorEvmContractAliases.isNativePrecompileAddress(null)).isFalse();
    }

    //    @Test
    //    void link() {
    //        mirrorEvmContractAliases.link(ALIAS, ADDRESS);
    //        ContractCallContext contractCallContext = ContractCallContext.get();
    //        assertThat(contractCallContext.getPendingAliases()).hasSize(1).containsEntry(ALIAS, ADDRESS);
    //        assertThat(contractCallContext.getPendingRemovals()).isEmpty();
    //    }

    //    @Test
    //    void unlink() {
    //        ContractCallContext contractCallContext = ContractCallContext.get();
    //        pendingAliases.put(ALIAS, ADDRESS);
    //
    //        mirrorEvmContractAliases.unlink(ALIAS);
    //
    //        assertThat(pendingAliases).isEmpty();
    //        assertThat(contractCallContext.getPendingRemovals()).hasSize(1).contains(ALIAS);
    //    }

    @Test
    void isInUseShouldBeTrueIfAliasesIsPresent() {
        given(store.exists(ALIAS)).willReturn(true);
        assertThat(mirrorEvmContractAliases.isInUse(ALIAS)).isTrue();
    }

    @Test
    void isInUseShouldBeFalseIfInAliasesAndInPendingRemovals() {
        given(store.exists(ALIAS)).willReturn(false);
        assertThat(mirrorEvmContractAliases.isInUse(ALIAS)).isFalse();
    }

    @Test
    void isInUseShouldBeFalseIfNotInAliasesOrPending() {
        assertThat(mirrorEvmContractAliases.isInUse(ALIAS)).isFalse();
    }

    @Test
    void publicKeyCouldNotBeParsed() throws InvalidProtocolBufferException, InvalidKeyException {
        final byte[] ecdsaPublicKey =
                Hex.decode("3a21033a514176466fa815ed481ffad09110a2d344f6c9b78c1d14afc351c3a51be33d");
        Address recoveredAddress = Address.fromHexString("0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b");
        Key key = Key.parseFrom(ecdsaPublicKey);
        JKey jKey = JKey.mapKey(key);
        mirrorEvmContractAliases.maybeLinkEvmAddress(jKey, ADDRESS);

        try (MockedStatic<EthSigsUtils> utilities = Mockito.mockStatic(EthSigsUtils.class)) {
            utilities
                    .when(() -> EthSigsUtils.recoverAddressFromPubKey((byte[]) any()))
                    .thenReturn(new byte[0]);
            given(store.exists(recoveredAddress)).willReturn(true);
            assertTrue(mirrorEvmContractAliases.isInUse(recoveredAddress));
        }
    }

    @Test
    void ignoresNullKeys() {
        assertFalse(mirrorEvmContractAliases.maybeLinkEvmAddress(null, ADDRESS));
    }
}
