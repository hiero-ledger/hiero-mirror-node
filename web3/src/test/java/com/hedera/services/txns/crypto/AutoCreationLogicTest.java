// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txns.crypto;

import static com.hedera.services.jproto.JKey.mapKey;
import static com.hedera.services.store.models.Id.fromGrpcToken;
import static com.hedera.services.utils.IdUtils.asAccount;
import static com.hedera.services.utils.IdUtils.asToken;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.common.util.DomainUtils.toEvmAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.node.app.service.evm.utils.EthSigsUtils;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.hapi.utils.fees.FeeObject;
import com.hedera.services.jproto.JKey;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.models.Account;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.web3.ContextExtension;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.account.MirrorEvmContractAliases;
import org.hiero.mirror.web3.evm.store.StackedStateFrames;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.StoreImpl;
import org.hiero.mirror.web3.evm.store.accessor.AccountDatabaseAccessor;
import org.hiero.mirror.web3.evm.store.accessor.DatabaseAccessor;
import org.hiero.mirror.web3.evm.store.accessor.EntityDatabaseAccessor;
import org.hiero.mirror.web3.evm.store.contract.EntityAddressSequencer;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(ContextExtension.class)
@ExtendWith(MockitoExtension.class)
class AutoCreationLogicTest {

    public static final AccountID PAYER = asAccount("0.0.12345");
    public static final TokenID TOKEN = asToken("0.0.23456");
    private static final long INITIAL_TRANSFER = 16L;
    private static final AccountID CREATED = asAccount("0.0.1234");
    private static final FeeObject FEES = new FeeObject(1L, 2L, 3L);
    private static final long TOTAL_FEE = 6L;
    private static final byte[] ECDSA_PUBLIC_KEY =
            Hex.decode("3a21033a514176466fa815ed481ffad09110a2d344f6c9b78c1d14afc351c3a51be33d");
    private final Timestamp at = Timestamp.newBuilder().setSeconds(1_234_567L).build();

    private final DomainBuilder domainBuilder = new DomainBuilder();

    @Captor
    private ArgumentCaptor<Account> accountCaptor;

    @Mock
    private MirrorEvmContractAliases aliasManager;

    @Mock
    private EntityDatabaseAccessor entityDatabaseAccessor;

    @Mock
    private EvmProperties evmProperties;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private EntityAddressSequencer ids;

    @Captor
    private ArgumentCaptor<Integer> maxAutoAssociationsCaptor;

    private Store store;

    private AutoCreationLogic subject;

    @Mock
    private SyntheticTxnFactory syntheticTxnFactory;

    @Mock
    private OptionValidator validator;

    @BeforeEach
    void setUp() {
        final List<DatabaseAccessor<Object, ?>> accessors = List.of(new AccountDatabaseAccessor(
                entityDatabaseAccessor, null, null, null, null, null, null, new SystemEntity(new CommonProperties())));
        final var stackedStateFrames = new StackedStateFrames(accessors);
        store = spy(new StoreImpl(stackedStateFrames, validator));
        subject = new AutoCreationLogic(feeCalculator, evmProperties, syntheticTxnFactory, aliasManager);
    }

    @Test
    void doesntAutoCreateWhenTokenTransferToAliasFeatureDisabled() {
        Key aPrimitiveKey = Key.newBuilder()
                .setEd25519(ByteString.copyFromUtf8("01234567890123456789012345678901"))
                .build();
        ByteString edKeyAlias = aPrimitiveKey.toByteString();

        given(evmProperties.isLazyCreationEnabled()).willReturn(false);

        final var input = wellKnownTokenChange(edKeyAlias);

        final var result = subject.create(input, at, store, ids, List.of(input));
        assertEquals(NOT_SUPPORTED, result.getLeft());
    }

    @Test
    void cannotCreateAccountFromUnaliasedChange() {
        final var input = BalanceChange.changingHbar(
                AccountAmount.newBuilder()
                        .setAmount(INITIAL_TRANSFER)
                        .setAccountID(PAYER)
                        .build(),
                PAYER);
        final var changes = List.of(input);
        assertThatThrownBy(() -> subject.create(input, at, store, ids, changes))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot auto-create an account from unaliased change");
    }

    @SneakyThrows
    @ParameterizedTest
    @CsvSource(textBlock = """
            54, -1
            53, -1
            52, 0
            """)
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "Native secp256k1 DLL not available on Windows")
    void hollowAccountWithHbarChangeWorks(int hapiMinorVersion, int expectedMaxAutoAssociations) {
        final var jKey = mapKey(Key.parseFrom(ECDSA_PUBLIC_KEY));
        final var evmAddressAlias =
                ByteString.copyFrom(EthSigsUtils.recoverAddressFromPubKey(jKey.getECDSASecp256k1Key()));
        TransactionBody.Builder syntheticHollowCreation =
                TransactionBody.newBuilder().setCryptoCreateAccount(CryptoCreateTransactionBody.newBuilder());

        ContractCallContext.get().setRecordFile(recordFileWithVersion(hapiMinorVersion));
        given(feeCalculator.computeFee(any(), any(), eq(at))).willReturn(FEES);
        given(ids.getNewAccountId()).willReturn(CREATED);
        given(syntheticTxnFactory.createHollowAccount(eq(evmAddressAlias), eq(0L), anyInt()))
                .willReturn(syntheticHollowCreation);

        final var input = wellKnownChange(evmAddressAlias);

        store.wrap();

        final var result = subject.create(input, at, store, ids, List.of(input));

        assertEquals(INITIAL_TRANSFER, input.getAggregatedUnits());
        assertEquals(INITIAL_TRANSFER, input.getNewBalance());
        assertEquals(Pair.of(OK, TOTAL_FEE * 2), result);
        verify(aliasManager)
                .link(
                        Address.wrap(Bytes.wrap(evmAddressAlias.toByteArray())),
                        Address.wrap(Bytes.wrap(toEvmAddress(CREATED))));
        verify(syntheticTxnFactory)
                .createHollowAccount(eq(evmAddressAlias), eq(0L), maxAutoAssociationsCaptor.capture());
        assertThat(maxAutoAssociationsCaptor.getValue()).isEqualTo(expectedMaxAutoAssociations);
        verify(store).updateAccount(accountCaptor.capture());
        assertThat(accountCaptor.getValue())
                .returns(expectedMaxAutoAssociations, Account::getMaxAutoAssociations)
                .returns(0, Account::getUsedAutoAssociations);
    }

    @SneakyThrows
    @ParameterizedTest
    @CsvSource(textBlock = """
            54, -1
            53, -1
            52, 1
            """)
    void happyPathWithFungibleTokenChangeWorks(int hapiMinorVersion, int expectedMaxAutoAssociations) {
        Key aPrimitiveKey = Key.newBuilder()
                .setEd25519(ByteString.copyFromUtf8("01234567890123456789012345678901"))
                .build();
        ByteString edKeyAlias = aPrimitiveKey.toByteString();
        TransactionBody.Builder syntheticEDAliasCreation = TransactionBody.newBuilder()
                .setCryptoCreateAccount(CryptoCreateTransactionBody.newBuilder().setAlias(edKeyAlias));

        final var input = wellKnownTokenChange(edKeyAlias);
        final var changes = List.of(input);

        ContractCallContext.get().setRecordFile(recordFileWithVersion(hapiMinorVersion));
        given(ids.getNewAccountId()).willReturn(CREATED);
        given(feeCalculator.computeFee(any(), any(), eq(at))).willReturn(FEES);
        given(evmProperties.isLazyCreationEnabled()).willReturn(true);
        given(syntheticTxnFactory.createAccount(eq(edKeyAlias), eq(aPrimitiveKey), eq(0L), anyInt()))
                .willReturn(syntheticEDAliasCreation);

        store.wrap();
        final var result = subject.create(input, at, store, ids, changes);

        assertEquals(INITIAL_TRANSFER, input.getAggregatedUnits());
        verify(aliasManager)
                .maybeLinkEvmAddress(JKey.mapKey(aPrimitiveKey), Address.wrap(Bytes.wrap(toEvmAddress(CREATED))));
        assertEquals(Pair.of(OK, TOTAL_FEE), result);
        verify(syntheticTxnFactory)
                .createAccount(eq(edKeyAlias), eq(aPrimitiveKey), eq(0L), maxAutoAssociationsCaptor.capture());
        assertThat(maxAutoAssociationsCaptor.getValue()).isEqualTo(expectedMaxAutoAssociations);
        verify(store).updateAccount(accountCaptor.capture());
        assertThat(accountCaptor.getValue())
                .returns(expectedMaxAutoAssociations, Account::getMaxAutoAssociations)
                .returns(0, Account::getUsedAutoAssociations);
    }

    @Test
    void analyzesTokenTransfersInChangesForAutoCreation() {
        final Key aPrimitiveKey = Key.newBuilder()
                .setEd25519(ByteString.copyFromUtf8("01234567890123456789012345678901"))
                .build();
        final ByteString edKeyAlias = aPrimitiveKey.toByteString();
        final TransactionBody.Builder syntheticEDAliasCreation = TransactionBody.newBuilder()
                .setCryptoCreateAccount(CryptoCreateTransactionBody.newBuilder().setAlias(edKeyAlias));
        given(ids.getNewAccountId()).willReturn(CREATED);
        given(feeCalculator.computeFee(any(), any(), eq(at))).willReturn(FEES);
        given(evmProperties.isLazyCreationEnabled()).willReturn(true);
        given(syntheticTxnFactory.createAccount(edKeyAlias, aPrimitiveKey, 0L, 2))
                .willReturn(syntheticEDAliasCreation);

        final var input1 = wellKnownTokenChange(edKeyAlias);
        final var input2 = anotherTokenChange();

        store.wrap();
        final var result = subject.create(input1, at, store, ids, List.of(input1, input2));
        assertEquals(Pair.of(OK, TOTAL_FEE), result);

        assertEquals(16L, input1.getAggregatedUnits());
    }

    private BalanceChange wellKnownTokenChange(final ByteString alias) {
        return BalanceChange.changingFtUnits(
                fromGrpcToken(TOKEN),
                TOKEN,
                AccountAmount.newBuilder()
                        .setAmount(INITIAL_TRANSFER)
                        .setAccountID(AccountID.newBuilder().setAlias(alias).build())
                        .build(),
                PAYER);
    }

    private BalanceChange wellKnownChange(final ByteString alias) {
        return BalanceChange.changingHbar(
                AccountAmount.newBuilder()
                        .setAmount(INITIAL_TRANSFER)
                        .setAccountID(AccountID.newBuilder().setAlias(alias).build())
                        .build(),
                PAYER);
    }

    private BalanceChange anotherTokenChange() {
        Key primitiveKey = Key.newBuilder()
                .setEd25519(ByteString.copyFromUtf8("01234567890123456789012345678901"))
                .build();
        final TokenID token1 = IdUtils.asToken("0.0.123456");
        return BalanceChange.changingFtUnits(
                fromGrpcToken(token1),
                token1,
                AccountAmount.newBuilder()
                        .setAmount(INITIAL_TRANSFER)
                        .setAccountID(AccountID.newBuilder()
                                .setAlias(primitiveKey.toByteString())
                                .build())
                        .build(),
                PAYER);
    }

    private RecordFile recordFileWithVersion(int hapiMinorVersion) {
        return domainBuilder
                .recordFile()
                .customize(rf -> rf.hapiVersionMinor(hapiMinorVersion))
                .get();
    }
}
