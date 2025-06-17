// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.ledger;

import static com.hedera.services.store.models.UniqueToken.getEmptyUniqueToken;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hedera.services.utils.EntityNum.fromEvmAddress;
import static com.hedera.services.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.FcTokenAllowanceId;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.txns.crypto.AutoCreationLogic;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.web3.evm.account.MirrorEvmContractAliases;
import org.hiero.mirror.web3.evm.store.Store.OnMissing;
import org.hiero.mirror.web3.evm.store.StoreImpl;
import org.hiero.mirror.web3.evm.store.contract.EntityAddressSequencer;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class TransferLogicTest {
    private static final DomainBuilder domainBuilder = new DomainBuilder();

    private final long initialAllowance = 100L;

    private final AccountID payer = domainBuilder.entityId().toAccountID();
    private final AccountID owner = domainBuilder.entityId().toAccountID();
    private final TokenID fungibleTokenID = domainBuilder.entityId().toTokenID();
    private final TokenID anotherFungibleTokenID = domainBuilder.entityId().toTokenID();
    private final TokenID nonFungibleTokenID = domainBuilder.entityId().toTokenID();
    private final AccountID revokedSpender = domainBuilder.entityId().toAccountID();
    private final EntityNum payerNum = EntityNum.fromAccountId(payer);

    private final FcTokenAllowanceId fungibleAllowanceId =
            FcTokenAllowanceId.from(EntityNum.fromTokenId(fungibleTokenID), payerNum);
    private final TreeMap<EntityNum, Long> cryptoAllowances = new TreeMap<>(Map.of(payerNum, initialAllowance));
    private final TreeMap<EntityNum, Long> resultCryptoAllowances = new TreeMap<>(Map.of(payerNum, 50L));
    private final TreeMap<FcTokenAllowanceId, Long> fungibleAllowances =
            new TreeMap<>(Map.of(fungibleAllowanceId, initialAllowance));
    private final TreeMap<FcTokenAllowanceId, Long> resultFungibleAllowances =
            new TreeMap<>(Map.of(fungibleAllowanceId, 50L));

    @Mock
    private EntityAddressSequencer ids;

    private HederaTokenStore hederaTokenStore;
    private AutoCreationLogic autoCreationLogic;
    private MirrorEvmContractAliases mirrorEvmContractAliases;
    private StoreImpl store;
    private TransferLogic subject;

    @BeforeEach
    void setUp() {
        autoCreationLogic = mock(AutoCreationLogic.class);
        mirrorEvmContractAliases = mock(MirrorEvmContractAliases.class);
        hederaTokenStore = mock(HederaTokenStore.class);
        subject = new TransferLogic(autoCreationLogic, mirrorEvmContractAliases);
        store = mock(StoreImpl.class);
    }

    @Test
    void throwsIseOnNonEmptyAliasWithNullAutoCreationLogic() {
        final var firstAmount = 1_000L;
        final var firstAlias = ByteString.copyFromUtf8("fakeAccountAliasTest");
        final var inappropriateTrigger = BalanceChange.changingHbar(aliasedAa(firstAlias, firstAmount), payer);

        subject = new TransferLogic(null, mirrorEvmContractAliases);

        final var triggerList = List.of(inappropriateTrigger);
        assertThrows(IllegalStateException.class, () -> subject.doZeroSum(triggerList, store, ids, hederaTokenStore));
    }

    @Test
    void failedAutoCreation() {
        final var firstAmount = 1_000L;
        final var firstAlias = ByteString.copyFromUtf8("fakeAccountAliasTest");
        final var failingTrigger = BalanceChange.changingHbar(aliasedAa(firstAlias, firstAmount), payer);
        final var changes = List.of(failingTrigger);

        given(autoCreationLogic.create(eq(failingTrigger), any(), eq(store), eq(ids), eq(changes)))
                .willReturn(Pair.of(INSUFFICIENT_ACCOUNT_BALANCE, 0L));

        assertFailsWith(() -> subject.doZeroSum(changes, store, ids, hederaTokenStore), INSUFFICIENT_ACCOUNT_BALANCE);
    }

    @Test
    void autoCreatesWithNftTransferToAlias() {
        var account = new Account(0L, Id.fromGrpcAccount(owner), 200L);
        final var firstAlias = ByteString.copyFromUtf8("fakeAccountAliasTest");
        final var transfer = NftTransfer.newBuilder()
                .setSenderAccountID(payer)
                .setReceiverAccountID(
                        AccountID.newBuilder().setAlias(firstAlias).build())
                .setSerialNumber(20L)
                .build();
        final var nftTransfer = BalanceChange.changingNftOwnership(
                Id.fromGrpcToken(nonFungibleTokenID), nonFungibleTokenID, transfer, payer);
        final var changes = List.of(nftTransfer);

        given(store.getAccount(asTypedEvmAddress(nftTransfer.accountId()), OnMissing.THROW))
                .willReturn(account);
        var nft = getEmptyUniqueToken();
        given(store.getUniqueToken(any(), eq(OnMissing.THROW))).willReturn(nft);
        given(autoCreationLogic.create(eq(nftTransfer), any(), eq(store), eq(ids), eq(changes)))
                .willReturn(Pair.of(OK, 100L));
        given(hederaTokenStore.tryTokenChange(any())).willReturn(OK);

        subject.doZeroSum(changes, store, ids, hederaTokenStore);

        verify(autoCreationLogic).create(eq(nftTransfer), any(), eq(store), eq(ids), eq(changes));
    }

    @Test
    void autoCreatesWithFungibleTokenTransferToAlias() {
        var account = new Account(0L, Id.fromGrpcAccount(owner), 200L);
        final var firstAlias = ByteString.copyFromUtf8("fakeAccountAliasTest");
        final var fungibleTransfer = BalanceChange.changingFtUnits(
                Id.fromGrpcToken(fungibleTokenID), fungibleTokenID, aliasedAa(firstAlias, 10L), payer);
        final var anotherFungibleTransfer = BalanceChange.changingFtUnits(
                Id.fromGrpcToken(anotherFungibleTokenID), anotherFungibleTokenID, aliasedAa(firstAlias, 10L), payer);
        final var changes = List.of(fungibleTransfer, anotherFungibleTransfer);

        given(store.getAccount(asTypedEvmAddress(payer), OnMissing.THROW)).willReturn(account);
        given(autoCreationLogic.create(eq(fungibleTransfer), any(), eq(store), eq(ids), eq(changes)))
                .willReturn(Pair.of(OK, 100L));
        given(autoCreationLogic.create(eq(anotherFungibleTransfer), any(), eq(store), eq(ids), eq(changes)))
                .willReturn(Pair.of(OK, 100L));
        given(hederaTokenStore.tryTokenChange(any())).willReturn(OK);

        subject.doZeroSum(changes, store, ids, hederaTokenStore);

        verify(autoCreationLogic).create(eq(fungibleTransfer), any(), eq(store), eq(ids), eq(changes));
        verify(autoCreationLogic).create(eq(anotherFungibleTransfer), any(), eq(store), eq(ids), eq(changes));
    }

    @Test
    void replacesExistingAliasesInChanges() {
        try (MockedStatic<EntityNum> utilities = Mockito.mockStatic(EntityNum.class)) {
            final var firstAlias = ByteString.copyFromUtf8("fakeAccountAliasTest");
            utilities
                    .when(() -> fromEvmAddress(Address.wrap(Bytes.wrap(firstAlias.toByteArray()))))
                    .thenReturn(payerNum);
            final var fungibleTransfer = BalanceChange.changingFtUnits(
                    Id.fromGrpcToken(fungibleTokenID), fungibleTokenID, aliasedAa(firstAlias, 10L), payer);
            final var transfer = NftTransfer.newBuilder()
                    .setSenderAccountID(payer)
                    .setReceiverAccountID(
                            AccountID.newBuilder().setAlias(firstAlias).build())
                    .setSerialNumber(20L)
                    .build();
            final var nftTransfer = BalanceChange.changingNftOwnership(
                    Id.fromGrpcToken(nonFungibleTokenID), nonFungibleTokenID, transfer, payer);
            final var changes = List.of(fungibleTransfer, nftTransfer);
            var nft = getEmptyUniqueToken();

            given(store.getUniqueToken(any(), eq(OnMissing.THROW))).willReturn(nft);
            given(mirrorEvmContractAliases.resolveForEvm(any()))
                    .willReturn(Address.wrap(Bytes.wrap(firstAlias.toByteArray())));
            given(hederaTokenStore.tryTokenChange(any())).willReturn(OK);

            subject.doZeroSum(changes, store, ids, hederaTokenStore);

            verify(autoCreationLogic, never()).create(eq(fungibleTransfer), any(), eq(store), eq(ids), eq(changes));
            verify(autoCreationLogic, never()).create(eq(nftTransfer), any(), eq(store), eq(ids), eq(changes));
        }
    }

    @Test
    void happyPathHbarAllowance() {
        final var change = BalanceChange.changingHbar(allowanceAA(owner, -50L), payer);
        var account = new Account(0L, Id.fromGrpcAccount(owner), 0L).setCryptoAllowance(cryptoAllowances);
        var spyAccount = spy(account);
        given(store.getAccount(asTypedEvmAddress(change.accountId()), OnMissing.THROW))
                .willReturn(spyAccount);

        subject.doZeroSum(List.of(change), store, ids, hederaTokenStore);

        verify(spyAccount).setCryptoAllowance(resultCryptoAllowances);
    }

    @Test
    void happyPathFungibleAllowance() {
        final var change = BalanceChange.changingFtUnits(
                Id.fromGrpcToken(fungibleTokenID), fungibleTokenID, allowanceAA(owner, -50L), payer);
        given(hederaTokenStore.tryTokenChange(change)).willReturn(OK);
        var account = new Account(0L, Id.fromGrpcAccount(owner), 0L).setFungibleTokenAllowances(fungibleAllowances);
        var spyAccount = spy(account);
        given(store.getAccount(asTypedEvmAddress(change.accountId()), OnMissing.THROW))
                .willReturn(spyAccount);

        assertDoesNotThrow(() -> subject.doZeroSum(List.of(change), store, ids, hederaTokenStore));
        verify(spyAccount).setFungibleTokenAllowances(resultFungibleAllowances);
    }

    @Test
    void happyPathNFTAllowance() {
        final var nftId1 = withDefaultShardRealm(nonFungibleTokenID, 1L);
        final var nftId2 = withDefaultShardRealm(nonFungibleTokenID, 2L);
        final var change1 = BalanceChange.changingNftOwnership(
                Id.fromGrpcToken(nonFungibleTokenID),
                nonFungibleTokenID,
                allowanceNftTransfer(owner, revokedSpender, 1L),
                payer);
        final var change2 = BalanceChange.changingNftOwnership(
                Id.fromGrpcToken(nonFungibleTokenID),
                nonFungibleTokenID,
                allowanceNftTransfer(owner, revokedSpender, 123L),
                payer);
        final var change3 = BalanceChange.changingNftOwnership(
                Id.fromGrpcToken(nonFungibleTokenID),
                nonFungibleTokenID,
                nftTransfer(owner, revokedSpender, 2L),
                payer);
        var nft = getEmptyUniqueToken();
        var spyNft = spy(nft);

        given(hederaTokenStore.tryTokenChange(change1)).willReturn(OK);
        given(hederaTokenStore.tryTokenChange(change2)).willReturn(OK);
        given(hederaTokenStore.tryTokenChange(change3)).willReturn(OK);
        given(store.getUniqueToken(nftId1, OnMissing.THROW)).willReturn(spyNft);
        given(store.getUniqueToken(nftId2, OnMissing.THROW)).willReturn(spyNft);
        given(store.getUniqueToken(withDefaultShardRealm(nonFungibleTokenID, 123L), OnMissing.THROW))
                .willReturn(spyNft);

        assertDoesNotThrow(() -> subject.doZeroSum(List.of(change1, change2, change3), store, ids, hederaTokenStore));
        verify(spyNft, times(3)).setSpender(Id.DEFAULT);
    }

    private AccountAmount aliasedAa(final ByteString alias, final long amount) {
        return AccountAmount.newBuilder()
                .setAccountID(AccountID.newBuilder().setAlias(alias))
                .setAmount(amount)
                .build();
    }

    private AccountAmount allowanceAA(final AccountID accountID, final long amount) {
        return AccountAmount.newBuilder()
                .setAccountID(accountID)
                .setAmount(amount)
                .setIsApproval(true)
                .build();
    }

    private NftTransfer allowanceNftTransfer(final AccountID sender, final AccountID receiver, final long serialNum) {
        return NftTransfer.newBuilder()
                .setIsApproval(true)
                .setSenderAccountID(sender)
                .setReceiverAccountID(receiver)
                .setSerialNumber(serialNum)
                .build();
    }

    private NftTransfer nftTransfer(final AccountID sender, final AccountID receiver, final long serialNum) {
        return NftTransfer.newBuilder()
                .setIsApproval(false)
                .setSenderAccountID(sender)
                .setReceiverAccountID(receiver)
                .setSerialNumber(serialNum)
                .build();
    }

    private static NftId withDefaultShardRealm(final TokenID tokenID, final long serialNo) {
        return new NftId(tokenID.getShardNum(), tokenID.getRealmNum(), tokenID.getTokenNum(), serialNo);
    }
}
