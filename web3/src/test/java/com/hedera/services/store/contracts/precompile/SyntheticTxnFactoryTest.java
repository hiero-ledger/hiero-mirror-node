// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.fees.calculation.utils.AccessorBasedUsages.THREE_MONTHS_IN_SECONDS;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.*;
import static com.hedera.services.store.contracts.precompile.SyntheticTxnFactory.AUTO_MEMO;
import static com.hedera.services.store.contracts.precompile.codec.Association.multiAssociation;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.EMPTY_KEY;
import static com.hedera.services.utils.EntityIdUtils.contractIdFromEvmAddress;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;
import static org.hiero.mirror.web3.evm.account.AccountAccessorImpl.EVM_ADDRESS_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GrantRevokeKycWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenFreezeUnfreezeWrapper;
import com.hedera.services.store.contracts.precompile.codec.ApproveWrapper;
import com.hedera.services.store.contracts.precompile.codec.BurnWrapper;
import com.hedera.services.store.contracts.precompile.codec.Dissociation;
import com.hedera.services.store.contracts.precompile.codec.KeyValueWrapper;
import com.hedera.services.store.contracts.precompile.codec.MintWrapper;
import com.hedera.services.store.contracts.precompile.codec.PauseWrapper;
import com.hedera.services.store.contracts.precompile.codec.SetApprovalForAllWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenExpiryWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenKeyWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenUpdateExpiryInfoWrapper;
import com.hedera.services.store.contracts.precompile.codec.UnpauseWrapper;
import com.hedera.services.store.contracts.precompile.codec.WipeWrapper;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyntheticTxnFactoryTest {
    private static final DomainBuilder domainBuilder = new DomainBuilder();
    private static final long SERIAL_NO = 100;
    private static final long SECOND_AMOUNT = 200;
    private static final AccountID ACCOUNT_A = domainBuilder.entityId().toAccountID();
    private static final AccountID ACCOUNT_B = domainBuilder.entityId().toAccountID();
    private static final AccountID ACCOUNT_C = domainBuilder.entityId().toAccountID();
    private static final TokenID FUNGIBLE = domainBuilder.entityId().toTokenID();
    private static final TokenID NON_FUNGIBLE = domainBuilder.entityId().toTokenID();
    private static final List<Long> TARGET_SERIAL_NOS = List.of(1L, 2L, 3L);
    private static final List<ByteString> NEW_METADATA =
            List.of(ByteString.copyFromUtf8("AAA"), ByteString.copyFromUtf8("BBB"), ByteString.copyFromUtf8("CCC"));
    public static final String HTS_PRECOMPILED_CONTRACT_ADDRESS = "0x167";
    public static final TokenID TOKEN = domainBuilder.entityId().toTokenID();
    public static final Id SENDER_ID = Id.fromGrpcAccount(sender);

    private SyntheticTxnFactory subject = new SyntheticTxnFactory();

    @Test
    void createsExpectedCryptoCreateWithEDKeyAlias() {
        final var balance = 10L;
        final var bytes = new byte[33];
        bytes[0] = 0x02;
        final Key key =
                Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(bytes)).build();
        final var alias = key.toByteString();
        final var result = subject.createAccount(alias, key, balance, 0);
        final var txnBody = result.build();

        assertTrue(txnBody.hasCryptoCreateAccount());
        assertEquals(AUTO_MEMO, txnBody.getCryptoCreateAccount().getMemo());
        assertEquals(
                THREE_MONTHS_IN_SECONDS,
                txnBody.getCryptoCreateAccount().getAutoRenewPeriod().getSeconds());
        assertEquals(10L, txnBody.getCryptoCreateAccount().getInitialBalance());
        assertEquals(0L, txnBody.getCryptoCreateAccount().getMaxAutomaticTokenAssociations());
        assertEquals(
                key.toByteString(), txnBody.getCryptoCreateAccount().getKey().toByteString());
        assertEquals(alias, txnBody.getCryptoCreateAccount().getAlias());
    }

    @Test
    void createsExpectedCryptoCreateWithECKeyAlias() {
        final var balance = 10L;
        final var bytes = new byte[33];
        bytes[0] = 0x02;
        final Key key =
                Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(bytes)).build();
        final var alias = key.toByteString();
        final var result = subject.createAccount(alias, key, balance, 0);
        final var txnBody = result.build();

        assertTrue(txnBody.hasCryptoCreateAccount());
        assertEquals(AUTO_MEMO, txnBody.getCryptoCreateAccount().getMemo());
        assertEquals(
                THREE_MONTHS_IN_SECONDS,
                txnBody.getCryptoCreateAccount().getAutoRenewPeriod().getSeconds());
        assertEquals(10L, txnBody.getCryptoCreateAccount().getInitialBalance());
        assertEquals(0L, txnBody.getCryptoCreateAccount().getMaxAutomaticTokenAssociations());
        assertEquals(
                key.toByteString(), txnBody.getCryptoCreateAccount().getKey().toByteString());
        assertEquals(alias, txnBody.getCryptoCreateAccount().getAlias());
    }

    @Test
    void createsExpectedHollowAccountCreate() {
        final var balance = 10L;
        final var evmAddressAlias = ByteString.copyFrom(Hex.decode("a94f5374fce5edbc8e2a8697c15331677e6ebf0b"));
        final var result = subject.createHollowAccount(evmAddressAlias, balance, 1);
        final var txnBody = result.build();

        assertTrue(txnBody.hasCryptoCreateAccount());
        assertEquals(asKeyUnchecked(EMPTY_KEY), txnBody.getCryptoCreateAccount().getKey());
        assertEquals(evmAddressAlias, txnBody.getCryptoCreateAccount().getAlias());
        assertEquals(
                EVM_ADDRESS_SIZE, txnBody.getCryptoCreateAccount().getAlias().size());
        assertEquals("lazy-created account", txnBody.getCryptoCreateAccount().getMemo());
        assertEquals(
                THREE_MONTHS_IN_SECONDS,
                txnBody.getCryptoCreateAccount().getAutoRenewPeriod().getSeconds());
        assertEquals(10L, txnBody.getCryptoCreateAccount().getInitialBalance());
        assertEquals(1L, txnBody.getCryptoCreateAccount().getMaxAutomaticTokenAssociations());
    }

    @Test
    void fungibleTokenChangeAddsAutoAssociations() {
        final var balance = 10L;
        final var bytes = new byte[33];
        bytes[0] = 0x02;
        final Key key =
                Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(bytes)).build();
        final var alias = key.toByteString();
        final var result = subject.createAccount(alias, key, balance, 1);
        final var txnBody = result.build();

        assertTrue(txnBody.hasCryptoCreateAccount());
        assertEquals(AUTO_MEMO, txnBody.getCryptoCreateAccount().getMemo());
        assertEquals(
                THREE_MONTHS_IN_SECONDS,
                txnBody.getCryptoCreateAccount().getAutoRenewPeriod().getSeconds());
        assertEquals(10L, txnBody.getCryptoCreateAccount().getInitialBalance());
        assertEquals(1, txnBody.getCryptoCreateAccount().getMaxAutomaticTokenAssociations());
        assertEquals(
                key.toByteString(), txnBody.getCryptoCreateAccount().getKey().toByteString());
        assertEquals(alias, txnBody.getCryptoCreateAccount().getAlias());
    }

    @Test
    void createsExpectedAssociations() {
        final var tokens = List.of(FUNGIBLE, NON_FUNGIBLE);
        final var associations = multiAssociation(ACCOUNT_A, tokens);

        final var result = subject.createAssociate(associations);
        final var txnBody = result.build();

        assertEquals(ACCOUNT_A, txnBody.getTokenAssociate().getAccount());
        assertEquals(tokens, txnBody.getTokenAssociate().getTokensList());
    }

    @Test
    void createsExpectedDissociations() {
        final var tokens = List.of(FUNGIBLE, NON_FUNGIBLE);
        final var associations = Dissociation.multiDissociation(ACCOUNT_A, tokens);

        final var result = subject.createDissociate(associations);
        final var txnBody = result.build();

        assertEquals(ACCOUNT_A, txnBody.getTokenDissociate().getAccount());
        assertEquals(tokens, txnBody.getTokenDissociate().getTokensList());
    }

    @Test
    void createsExpectedNftMint() {
        final var nftMints = MintWrapper.forNonFungible(NON_FUNGIBLE, NEW_METADATA);

        final var result = subject.createMint(nftMints);
        final var txnBody = result.build();

        assertEquals(NON_FUNGIBLE, txnBody.getTokenMint().getToken());
        assertEquals(NEW_METADATA, txnBody.getTokenMint().getMetadataList());
    }

    @Test
    void createsExpectedNftBurn() {
        final var nftBurns = BurnWrapper.forNonFungible(NON_FUNGIBLE, TARGET_SERIAL_NOS);

        final var result = subject.createBurn(nftBurns);
        final var txnBody = result.build();

        assertEquals(NON_FUNGIBLE, txnBody.getTokenBurn().getToken());
        assertEquals(TARGET_SERIAL_NOS, txnBody.getTokenBurn().getSerialNumbersList());
    }

    @Test
    void createsExpectedFungibleMint() {
        final var amount = 1234L;
        final var funMints = MintWrapper.forFungible(FUNGIBLE, amount);

        final var result = subject.createMint(funMints);
        final var txnBody = result.build();

        assertEquals(FUNGIBLE, txnBody.getTokenMint().getToken());
        assertEquals(amount, txnBody.getTokenMint().getAmount());
    }

    @Test
    void createsExpectedFungibleBurn() {
        final var amount = 1234L;
        final var funBurns = BurnWrapper.forFungible(FUNGIBLE, amount);

        final var result = subject.createBurn(funBurns);
        final var txnBody = result.build();

        assertEquals(FUNGIBLE, txnBody.getTokenBurn().getToken());
        assertEquals(amount, txnBody.getTokenBurn().getAmount());
    }

    @Test
    void createsExpectedGrantKycCall() {
        final var grantWrapper = new GrantRevokeKycWrapper<>(FUNGIBLE, ACCOUNT_A);
        final var result = subject.createGrantKyc(grantWrapper);
        final var txnBody = result.build();

        assertEquals(FUNGIBLE, txnBody.getTokenGrantKyc().getToken());
        assertEquals(ACCOUNT_A, txnBody.getTokenGrantKyc().getAccount());
    }

    @Test
    void createsExpectedFungibleWipe() {
        final var amount = 1234L;
        final var fungibleWipe = WipeWrapper.forFungible(FUNGIBLE, ACCOUNT_A, amount);

        final var result = subject.createWipe(fungibleWipe);
        final var txnBody = result.build();

        assertEquals(FUNGIBLE, txnBody.getTokenWipe().getToken());
        assertEquals(amount, txnBody.getTokenWipe().getAmount());
        assertEquals(ACCOUNT_A, txnBody.getTokenWipe().getAccount());
    }

    @Test
    void createsExpectedFungibleApproveAllowance() {
        final var amount = BigInteger.ONE;
        final var allowances = new ApproveWrapper(TOKEN, receiver, amount, BigInteger.ZERO, true, false);

        final var result = subject.createFungibleApproval(allowances, SENDER_ID);
        final var txnBody = result.build();

        assertEquals(
                amount.longValue(),
                txnBody.getCryptoApproveAllowance().getTokenAllowances(0).getAmount());
        assertEquals(
                TOKEN, txnBody.getCryptoApproveAllowance().getTokenAllowances(0).getTokenId());
        final var allowance = txnBody.getCryptoApproveAllowance().getTokenAllowances(0);
        assertEquals(SENDER_ID.asGrpcAccount(), allowance.getOwner());
        assertEquals(receiver, allowance.getSpender());
    }

    @Test
    void createsExpectedNonfungibleApproveAllowanceWithOwnerAsOperator() {
        final var allowances = new ApproveWrapper(TOKEN, receiver, BigInteger.ZERO, BigInteger.ONE, false, false);
        final var ownerId = new Id(0, 0, 666);

        final var result = subject.createNonfungibleApproval(allowances, ownerId, ownerId);
        final var txnBody = result.build();

        final var allowance = txnBody.getCryptoApproveAllowance().getNftAllowances(0);
        assertEquals(TOKEN, allowance.getTokenId());
        assertEquals(receiver, allowance.getSpender());
        assertEquals(ownerId.asGrpcAccount(), allowance.getOwner());
        assertEquals(AccountID.getDefaultInstance(), allowance.getDelegatingSpender());
        assertEquals(1L, allowance.getSerialNumbers(0));
    }

    @Test
    void createsExpectedNonfungibleApproveAllowanceWithNonOwnerOperator() {
        final var allowances = new ApproveWrapper(TOKEN, receiver, BigInteger.ZERO, BigInteger.ONE, false, false);
        final var ownerId = new Id(0, 0, 666);
        final var operatorId = new Id(0, 0, 777);

        final var result = subject.createNonfungibleApproval(allowances, ownerId, operatorId);
        final var txnBody = result.build();

        final var allowance = txnBody.getCryptoApproveAllowance().getNftAllowances(0);
        assertEquals(TOKEN, allowance.getTokenId());
        assertEquals(receiver, allowance.getSpender());
        assertEquals(ownerId.asGrpcAccount(), allowance.getOwner());
        assertEquals(operatorId.asGrpcAccount(), allowance.getDelegatingSpender());
        assertEquals(1L, allowance.getSerialNumbers(0));
    }

    @Test
    void createsExpectedNonfungibleApproveAllowanceWithoutOwner() {
        final var allowances = new ApproveWrapper(TOKEN, receiver, BigInteger.ZERO, BigInteger.ONE, false, false);
        final var operatorId = new Id(0, 0, 666);

        final var result = subject.createNonfungibleApproval(allowances, null, operatorId);
        final var txnBody = result.build();

        final var allowance = txnBody.getCryptoApproveAllowance().getNftAllowances(0);
        assertEquals(TOKEN, allowance.getTokenId());
        assertEquals(receiver, allowance.getSpender());
        assertEquals(AccountID.getDefaultInstance(), allowance.getOwner());
        assertEquals(1L, allowance.getSerialNumbers(0));
    }

    @Test
    void createsDeleteAllowance() {
        final var allowances = new ApproveWrapper(TOKEN, receiver, BigInteger.ZERO, BigInteger.ONE, false, false);

        final var result = subject.createDeleteAllowance(allowances, SENDER_ID);
        final var txnBody = result.build();

        assertEquals(
                TOKEN, txnBody.getCryptoDeleteAllowance().getNftAllowances(0).getTokenId());
        assertEquals(1L, txnBody.getCryptoDeleteAllowance().getNftAllowances(0).getSerialNumbers(0));
        assertEquals(
                HTSTestsUtil.sender,
                txnBody.getCryptoDeleteAllowance().getNftAllowances(0).getOwner());
    }

    @Test
    void createsExpectedNftWipe() {
        final var nftWipe = WipeWrapper.forNonFungible(NON_FUNGIBLE, ACCOUNT_A, TARGET_SERIAL_NOS);

        final var result = subject.createWipe(nftWipe);
        final var txnBody = result.build();

        assertEquals(NON_FUNGIBLE, txnBody.getTokenWipe().getToken());
        assertEquals(ACCOUNT_A, txnBody.getTokenWipe().getAccount());
        assertEquals(TARGET_SERIAL_NOS, txnBody.getTokenWipe().getSerialNumbersList());
    }

    @Test
    void createsAdjustAllowanceForAllNFT() {
        final var allowances = new SetApprovalForAllWrapper(NON_FUNGIBLE, receiver, true);

        final var result = subject.createApproveAllowanceForAllNFT(allowances, SENDER_ID);
        final var txnBody = result.build();

        assertEquals(
                receiver,
                txnBody.getCryptoApproveAllowance().getNftAllowances(0).getSpender());
        assertEquals(
                sender, txnBody.getCryptoApproveAllowance().getNftAllowances(0).getOwner());
        assertEquals(
                NON_FUNGIBLE,
                txnBody.getCryptoApproveAllowance().getNftAllowances(0).getTokenId());
        assertEquals(
                BoolValue.of(true),
                txnBody.getCryptoApproveAllowance().getNftAllowances(0).getApprovedForAll());
    }

    @Test
    void createsExpectedFreeze() {
        final var freezeWrapper = TokenFreezeUnfreezeWrapper.forFreeze(FUNGIBLE, ACCOUNT_A);
        final var result = subject.createFreeze(freezeWrapper);
        final var txnBody = result.build();

        assertEquals(FUNGIBLE, txnBody.getTokenFreeze().getToken());
        assertEquals(ACCOUNT_A, txnBody.getTokenFreeze().getAccount());
    }

    @Test
    void createsExpectedUnfreeze() {
        final var unfreezeWrapper = TokenFreezeUnfreezeWrapper.forUnfreeze(FUNGIBLE, ACCOUNT_A);
        final var result = subject.createUnfreeze(unfreezeWrapper);
        final var txnBody = result.build();

        assertEquals(FUNGIBLE, txnBody.getTokenUnfreeze().getToken());
        assertEquals(ACCOUNT_A, txnBody.getTokenUnfreeze().getAccount());
    }

    @Test
    void createsExpectedPause() {
        final var pauseWrapper = new PauseWrapper(FUNGIBLE);
        final var result = subject.createPause(pauseWrapper);
        final var txnBody = result.build();

        assertEquals(FUNGIBLE, txnBody.getTokenPause().getToken());
    }

    @Test
    void createsExpectedUnpause() {
        final var unpauseWrapper = new UnpauseWrapper(FUNGIBLE);
        final var result = subject.createUnpause(unpauseWrapper);
        final var txnBody = result.build();

        assertEquals(FUNGIBLE, txnBody.getTokenUnpause().getToken());
    }

    @Test
    void createsExpectedTransactionCall() {
        final var result = subject.createTransactionCall(1, Bytes.of(1));
        final var txnBody = result.build();

        assertTrue(result.hasContractCall());
        assertEquals(1, txnBody.getContractCall().getGas());
        assertEquals(
                contractIdFromEvmAddress(
                        Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS).toArray()),
                txnBody.getContractCall().getContractID());
        assertEquals(
                ByteString.copyFrom(Bytes.of(1).toArray()),
                txnBody.getContractCall().getFunctionParameters());
    }

    @Test
    void createsExpectedCryptoTransfer() {
        final var fungibleTransfer = new FungibleTokenTransfer(SECOND_AMOUNT, false, FUNGIBLE, ACCOUNT_B, ACCOUNT_A);

        final var result = subject.createCryptoTransfer(
                List.of(new TokenTransferWrapper(Collections.emptyList(), List.of(fungibleTransfer))));
        final var txnBody = result.build();

        final var tokenTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();
        final var expFungibleTransfer = tokenTransfers.get(0);
        assertEquals(FUNGIBLE, expFungibleTransfer.getToken());
        assertEquals(
                List.of(fungibleTransfer.senderAdjustment(), fungibleTransfer.receiverAdjustment()),
                expFungibleTransfer.getTransfersList());
    }

    @Test
    void mergesRepeatedTokenIds() {
        final var fungibleTransfer = new FungibleTokenTransfer(SECOND_AMOUNT, false, FUNGIBLE, ACCOUNT_B, ACCOUNT_A);
        final var nonFungibleTransfer = new NftExchange(1L, NON_FUNGIBLE, ACCOUNT_A, ACCOUNT_B);
        assertFalse(nonFungibleTransfer.isApproval());

        final var result = subject.createCryptoTransfer(List.of(
                new TokenTransferWrapper(Collections.emptyList(), List.of(fungibleTransfer)),
                new TokenTransferWrapper(Collections.emptyList(), List.of(fungibleTransfer)),
                new TokenTransferWrapper(List.of(nonFungibleTransfer), Collections.emptyList())));

        final var txnBody = result.build();

        final var finalTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();
        assertEquals(2, finalTransfers.size());
        final var mergedFungible = finalTransfers.get(0);
        assertEquals(FUNGIBLE, mergedFungible.getToken());
        assertEquals(
                List.of(aaWith(ACCOUNT_B, -2 * SECOND_AMOUNT), aaWith(ACCOUNT_A, +2 * SECOND_AMOUNT)),
                mergedFungible.getTransfersList());
    }

    @Test
    void createsExpectedCryptoTransferForNFTTransfer() {
        final var nftExchange = new NftExchange(SERIAL_NO, NON_FUNGIBLE, ACCOUNT_A, ACCOUNT_C);

        final var result = subject.createCryptoTransfer(
                Collections.singletonList(new TokenTransferWrapper(List.of(nftExchange), Collections.emptyList())));
        final var txnBody = result.build();

        final var tokenTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();
        final var expNftTransfer = tokenTransfers.get(0);
        assertEquals(NON_FUNGIBLE, expNftTransfer.getToken());
        assertEquals(List.of(nftExchange.asGrpc()), expNftTransfer.getNftTransfersList());
        assertEquals(1, tokenTransfers.size());
    }

    @Test
    void createsExpectedCryptoTransferForFungibleTransfer() {
        final var fungibleTransfer = new FungibleTokenTransfer(SECOND_AMOUNT, false, FUNGIBLE, ACCOUNT_B, ACCOUNT_A);

        final var result = subject.createCryptoTransfer(Collections.singletonList(
                new TokenTransferWrapper(Collections.emptyList(), List.of(fungibleTransfer))));
        final var txnBody = result.build();

        final var tokenTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();
        final var expFungibleTransfer = tokenTransfers.get(0);
        assertEquals(FUNGIBLE, expFungibleTransfer.getToken());
        assertEquals(
                List.of(fungibleTransfer.senderAdjustment(), fungibleTransfer.receiverAdjustment()),
                expFungibleTransfer.getTransfersList());
        assertEquals(1, tokenTransfers.size());
    }

    @Test
    void createsExpectedCryptoTransfersForMultipleTransferWrappers() {
        final var nftExchange = new NftExchange(SERIAL_NO, NON_FUNGIBLE, ACCOUNT_A, ACCOUNT_C);
        final var fungibleTransfer = new FungibleTokenTransfer(SECOND_AMOUNT, false, FUNGIBLE, ACCOUNT_B, ACCOUNT_A);

        final var result = subject.createCryptoTransfer(List.of(
                new TokenTransferWrapper(Collections.emptyList(), List.of(fungibleTransfer)),
                new TokenTransferWrapper(List.of(nftExchange), Collections.emptyList())));
        final var txnBody = result.build();

        final var tokenTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();

        final var expFungibleTransfer = tokenTransfers.get(0);
        assertEquals(FUNGIBLE, expFungibleTransfer.getToken());
        assertEquals(
                List.of(fungibleTransfer.senderAdjustment(), fungibleTransfer.receiverAdjustment()),
                expFungibleTransfer.getTransfersList());

        final var expNftTransfer = tokenTransfers.get(1);
        assertEquals(NON_FUNGIBLE, expNftTransfer.getToken());
        assertEquals(List.of(nftExchange.asGrpc()), expNftTransfer.getNftTransfersList());
    }

    @Test
    void mergesFungibleTransfersAsExpected() {
        final var source = new TokenTransferWrapper(
                        Collections.emptyList(),
                        List.of(new FungibleTokenTransfer(1, false, FUNGIBLE, ACCOUNT_A, ACCOUNT_B)))
                .asGrpcBuilder();
        final var target = new TokenTransferWrapper(
                        Collections.emptyList(),
                        List.of(new FungibleTokenTransfer(2, false, FUNGIBLE, ACCOUNT_B, ACCOUNT_C)))
                .asGrpcBuilder();

        SyntheticTxnFactory.mergeTokenTransfers(target, source);

        assertEquals(FUNGIBLE, target.getToken());
        final var transfers = target.getTransfersList();
        assertEquals(List.of(aaWith(ACCOUNT_B, -1), aaWith(ACCOUNT_C, +2), aaWith(ACCOUNT_A, -1)), transfers);
    }

    @Test
    void createsExpectedCryptoTransferForFungibleAndHbarTransfer() {
        final var fungibleTransfer = new FungibleTokenTransfer(SECOND_AMOUNT, false, FUNGIBLE, ACCOUNT_B, ACCOUNT_A);

        final var hbarTransfer = new HbarTransfer(SECOND_AMOUNT, false, ACCOUNT_A, ACCOUNT_B);

        final var result = subject.createCryptoTransfer(Collections.singletonList(
                new TokenTransferWrapper(Collections.emptyList(), List.of(fungibleTransfer))));
        final var resultHBar = subject.createCryptoTransferForHbar(new TransferWrapper(List.of(hbarTransfer)));

        result.mergeFrom(resultHBar);

        final var txnBody = result.build();

        final var tokenTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();
        final var expFungibleTransfer = tokenTransfers.get(0);
        assertEquals(FUNGIBLE, expFungibleTransfer.getToken());
        assertEquals(
                List.of(fungibleTransfer.senderAdjustment(), fungibleTransfer.receiverAdjustment()),
                expFungibleTransfer.getTransfersList());
        assertEquals(1, tokenTransfers.size());

        final var hbarTransfers = txnBody.getCryptoTransfer().getTransfers();
        assertTrue(txnBody.getCryptoTransfer().hasTransfers());
        assertEquals(
                List.of(hbarTransfer.senderAdjustment(), hbarTransfer.receiverAdjustment()),
                hbarTransfers.getAccountAmountsList());
        assertEquals(2, hbarTransfers.getAccountAmountsList().size());
    }

    @Test
    void createsExpectedCryptoTransferForNFTAndHbarTransfer() {
        final var nftExchange = new NftExchange(SERIAL_NO, NON_FUNGIBLE, ACCOUNT_A, ACCOUNT_C);
        final var hbarTransfer = new HbarTransfer(SECOND_AMOUNT, false, ACCOUNT_A, ACCOUNT_B);

        final var result = subject.createCryptoTransfer(
                Collections.singletonList(new TokenTransferWrapper(List.of(nftExchange), Collections.emptyList())));

        final var resultHBar = subject.createCryptoTransferForHbar(new TransferWrapper(List.of(hbarTransfer)));

        result.mergeFrom(resultHBar);
        final var txnBody = result.build();

        final var tokenTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();
        final var expNftTransfer = tokenTransfers.get(0);
        assertEquals(NON_FUNGIBLE, expNftTransfer.getToken());
        assertEquals(List.of(nftExchange.asGrpc()), expNftTransfer.getNftTransfersList());
        assertEquals(1, tokenTransfers.size());

        final var hbarTransfers = txnBody.getCryptoTransfer().getTransfers();
        assertTrue(txnBody.getCryptoTransfer().hasTransfers());
        assertEquals(
                List.of(hbarTransfer.senderAdjustment(), hbarTransfer.receiverAdjustment()),
                hbarTransfers.getAccountAmountsList());
        assertEquals(2, hbarTransfers.getAccountAmountsList().size());
    }

    @Test
    void createsExpectedCryptoTransferHbarOnlyTransfer() {
        final var hbarTransfer = new HbarTransfer(SECOND_AMOUNT, false, ACCOUNT_A, ACCOUNT_B);

        final var result = subject.createCryptoTransfer(Collections.emptyList());
        final var resultHBar = subject.createCryptoTransferForHbar(new TransferWrapper(List.of(hbarTransfer)));

        result.mergeFrom(resultHBar);

        final var txnBody = result.build();

        final var tokenTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();
        assertEquals(0, tokenTransfers.size());

        final var hbarTransfers = txnBody.getCryptoTransfer().getTransfers();
        assertTrue(txnBody.getCryptoTransfer().hasTransfers());
        assertEquals(
                List.of(hbarTransfer.senderAdjustment(), hbarTransfer.receiverAdjustment()),
                hbarTransfers.getAccountAmountsList());
        assertEquals(2, hbarTransfers.getAccountAmountsList().size());
    }

    @Test
    void createsExpectedCryptoTransferTokensOnlyTransfer() {
        final var nftExchange = new NftExchange(SERIAL_NO, NON_FUNGIBLE, ACCOUNT_A, ACCOUNT_C);
        final var fungibleTransfer = new FungibleTokenTransfer(SECOND_AMOUNT, false, FUNGIBLE, ACCOUNT_B, ACCOUNT_A);

        final var result = subject.createCryptoTransfer(List.of(
                new TokenTransferWrapper(Collections.emptyList(), List.of(fungibleTransfer)),
                new TokenTransferWrapper(List.of(nftExchange), Collections.emptyList())));
        final var resultHBar = subject.createCryptoTransferForHbar(new TransferWrapper(Collections.emptyList()));

        result.mergeFrom(resultHBar);
        final var txnBody = result.build();

        final var tokenTransfers = txnBody.getCryptoTransfer().getTokenTransfersList();

        final var expFungibleTransfer = tokenTransfers.get(0);
        assertEquals(FUNGIBLE, expFungibleTransfer.getToken());
        assertEquals(
                List.of(fungibleTransfer.senderAdjustment(), fungibleTransfer.receiverAdjustment()),
                expFungibleTransfer.getTransfersList());

        final var expNftTransfer = tokenTransfers.get(1);
        assertEquals(NON_FUNGIBLE, expNftTransfer.getToken());
        assertEquals(List.of(nftExchange.asGrpc()), expNftTransfer.getNftTransfersList());

        final var hbarTransfers = txnBody.getCryptoTransfer().getTransfers();
        assertEquals(0, hbarTransfers.getAccountAmountsList().size());
        assertFalse(txnBody.getCryptoTransfer().hasTransfers());
    }

    @Test
    void acceptsEmptyWrappers() {
        final var result = subject.createCryptoTransfer(List.of());

        final var txnBody = result.build();
        assertEquals(0, txnBody.getCryptoTransfer().getTokenTransfersCount());
    }

    @Test
    void createsExpectedTokenUpdateCallForFungible() {
        // given
        final var adminKey = new KeyValueWrapper(
                false, null, new byte[] {}, new byte[] {}, contractIdFromEvmAddress(contractAddress));
        final var multiKey = new KeyValueWrapper(
                false, contractIdFromEvmAddress(contractAddress), new byte[] {}, new byte[] {}, null);
        final var tokenUpdateWrapper = createFungibleTokenUpdateWrapperWithKeys(
                List.of(new TokenKeyWrapper(112, multiKey), new TokenKeyWrapper(1, adminKey)));
        final var result = subject.createTokenUpdate(tokenUpdateWrapper);
        final var txnBody = result.build().getTokenUpdate();

        assertEquals(HTSTestsUtil.FUNGIBLE, txnBody.getToken());

        assertEquals("fungible", txnBody.getName());
        assertEquals("G", txnBody.getSymbol());
        assertEquals(account, txnBody.getTreasury());
        assertEquals("G token memo", txnBody.getMemo().getValue());
        assertEquals(1, txnBody.getExpiry().getSeconds());
        assertEquals(2, txnBody.getAutoRenewPeriod().getSeconds());
        assertTrue(txnBody.hasAutoRenewAccount());
    }

    @Test
    void createsExpectedTokenUpdateCallForNonFungible() {
        // given
        final var complexKey = new KeyValueWrapper(
                false, null, new byte[] {}, new byte[] {}, contractIdFromEvmAddress(contractAddress));
        final var multiKey = new KeyValueWrapper(
                false, contractIdFromEvmAddress(contractAddress), new byte[] {}, new byte[] {}, null);
        final var wrapper = createNonFungibleTokenUpdateWrapperWithKeys(List.of(
                new TokenKeyWrapper(112, multiKey),
                new TokenKeyWrapper(2, complexKey),
                new TokenKeyWrapper(4, complexKey),
                new TokenKeyWrapper(8, complexKey)));

        // when
        final var result = subject.createTokenUpdate(wrapper);
        final var txnBody = result.build().getTokenUpdate();

        // then

        assertEquals(0, txnBody.getExpiry().getSeconds());
        assertEquals(0, txnBody.getAutoRenewPeriod().getSeconds());
        assertFalse(txnBody.hasAutoRenewAccount());
    }

    @Test
    void createsExpectedUpdateTokenExpiryInfoWithZeroExpiry() {
        final var updateExpiryInfo = new TokenUpdateExpiryInfoWrapper(TOKEN, new TokenExpiryWrapper(0L, payer, 555L));

        final var result = subject.createTokenUpdateExpiryInfo(updateExpiryInfo);
        final var txnBody = result.build();

        assertEquals(TOKEN, txnBody.getTokenUpdate().getToken());
        assertEquals(0L, txnBody.getTokenUpdate().getExpiry().getSeconds());
        assertEquals(payer, txnBody.getTokenUpdate().getAutoRenewAccount());
        assertEquals(555L, txnBody.getTokenUpdate().getAutoRenewPeriod().getSeconds());
    }

    @Test
    void createsExpectedUpdateTokenExpiryInfoWithZeroAutoRenewPeriod() {
        final var updateExpiryInfo = new TokenUpdateExpiryInfoWrapper(TOKEN, new TokenExpiryWrapper(442L, payer, 0L));

        final var result = subject.createTokenUpdateExpiryInfo(updateExpiryInfo);
        final var txnBody = result.build();

        assertEquals(TOKEN, txnBody.getTokenUpdate().getToken());
        assertEquals(442L, txnBody.getTokenUpdate().getExpiry().getSeconds());
        assertEquals(payer, txnBody.getTokenUpdate().getAutoRenewAccount());
        assertEquals(0L, txnBody.getTokenUpdate().getAutoRenewPeriod().getSeconds());
    }

    @Test
    void createsExpectedUpdateTokenExpiryInfoWithNoAutoRenewAccount() {
        final var updateExpiryInfo = new TokenUpdateExpiryInfoWrapper(TOKEN, new TokenExpiryWrapper(442L, null, 555L));

        final var result = subject.createTokenUpdateExpiryInfo(updateExpiryInfo);
        final var txnBody = result.build();

        assertEquals(TOKEN, txnBody.getTokenUpdate().getToken());
        assertEquals(442L, txnBody.getTokenUpdate().getExpiry().getSeconds());
        assertTrue(txnBody.getTokenUpdate().getAutoRenewAccount().toString().isEmpty());
        assertEquals(555L, txnBody.getTokenUpdate().getAutoRenewPeriod().getSeconds());
    }

    @Test
    void createsExpectedFungibleTokenCreate() {
        // given
        final var adminKey = new KeyValueWrapper(
                false, null, new byte[] {}, new byte[] {}, contractIdFromEvmAddress(contractAddress));
        final var multiKey = new KeyValueWrapper(
                false, contractIdFromEvmAddress(contractAddress), new byte[] {}, new byte[] {}, null);
        final var wrapper = createTokenCreateWrapperWithKeys(
                List.of(new TokenKeyWrapper(254, multiKey), new TokenKeyWrapper(1, adminKey)));
        wrapper.setFixedFees(List.of(fixedFee));
        wrapper.setFractionalFees(List.of(fractionalFee));

        // when
        final var result = subject.createTokenCreate(wrapper);
        final var txnBody = result.build().getTokenCreation();

        // then
        assertTrue(result.hasTokenCreation());

        assertEquals(TokenType.FUNGIBLE_COMMON, txnBody.getTokenType());
        assertEquals("token", txnBody.getName());
        assertEquals("symbol", txnBody.getSymbol());
        assertEquals(account, txnBody.getTreasury());
        assertEquals("memo", txnBody.getMemo());
        assertEquals(TokenSupplyType.INFINITE, txnBody.getSupplyType());
        assertEquals(Long.MAX_VALUE, txnBody.getInitialSupply());
        assertEquals(Integer.MAX_VALUE, txnBody.getDecimals());
        assertEquals(5054L, txnBody.getMaxSupply());
        assertFalse(txnBody.getFreezeDefault());
        assertEquals(442L, txnBody.getExpiry().getSeconds());
        assertEquals(555L, txnBody.getAutoRenewPeriod().getSeconds());
        assertEquals(payer, txnBody.getAutoRenewAccount());

        // keys assertions
        assertKeys(txnBody, adminKey, multiKey);

        // assert custom fees
        assertEquals(2, txnBody.getCustomFeesCount());
        assertEquals(fixedFee.asGrpc(), txnBody.getCustomFees(0));
        assertEquals(fractionalFee.asGrpc(), txnBody.getCustomFees(1));
    }

    @Test
    void createsExpectedNonFungibleTokenCreate() {
        // given
        final var multiKey = new KeyValueWrapper(
                false, contractIdFromEvmAddress(contractAddress), new byte[] {}, new byte[] {}, null);
        final var wrapper =
                HTSTestsUtil.createNonFungibleTokenCreateWrapperWithKeys(List.of(new TokenKeyWrapper(255, multiKey)));
        wrapper.setFixedFees(List.of(fixedFee));
        wrapper.setRoyaltyFees(List.of(royaltyFee));

        // when
        final var result = subject.createTokenCreate(wrapper);
        final var txnBody = result.build().getTokenCreation();

        // then
        assertTrue(result.hasTokenCreation());

        assertEquals(TokenType.NON_FUNGIBLE_UNIQUE, txnBody.getTokenType());
        assertEquals("nft", txnBody.getName());
        assertEquals("NFT", txnBody.getSymbol());
        assertEquals(account, txnBody.getTreasury());
        assertEquals("nftMemo", txnBody.getMemo());
        assertEquals(TokenSupplyType.FINITE, txnBody.getSupplyType());
        assertEquals(0L, txnBody.getInitialSupply());
        assertEquals(0, txnBody.getDecimals());
        assertEquals(5054L, txnBody.getMaxSupply());
        assertTrue(txnBody.getFreezeDefault());
        assertEquals(0, txnBody.getExpiry().getSeconds());
        assertEquals(0, txnBody.getAutoRenewPeriod().getSeconds());
        assertFalse(txnBody.hasAutoRenewAccount());

        // keys assertions
        assertKeys(txnBody, multiKey, multiKey);

        // assert custom fees
        assertEquals(2, txnBody.getCustomFeesCount());
        assertEquals(fixedFee.asGrpc(), txnBody.getCustomFees(0));
        assertEquals(royaltyFee.asGrpc(), txnBody.getCustomFees(1));
    }

    private static AccountAmount aaWith(final AccountID accountID, final long amount) {
        return AccountAmount.newBuilder()
                .setAccountID(accountID)
                .setAmount(amount)
                .build();
    }

    private void assertKeys(TokenCreateTransactionBody txnBody, KeyValueWrapper adminKey, KeyValueWrapper multiKey) {
        assertTrue(txnBody.hasAdminKey());
        assertEquals(adminKey.asGrpc(), txnBody.getAdminKey());
        assertTrue(txnBody.hasKycKey());
        assertEquals(multiKey.asGrpc(), txnBody.getKycKey());
        assertTrue(txnBody.hasFreezeKey());
        assertEquals(multiKey.asGrpc(), txnBody.getFreezeKey());
        assertTrue(txnBody.hasWipeKey());
        assertEquals(multiKey.asGrpc(), txnBody.getWipeKey());
        assertTrue(txnBody.hasSupplyKey());
        assertEquals(multiKey.asGrpc(), txnBody.getSupplyKey());
        assertTrue(txnBody.hasFeeScheduleKey());
        assertEquals(multiKey.asGrpc(), txnBody.getFeeScheduleKey());
        assertTrue(txnBody.hasPauseKey());
        assertEquals(multiKey.asGrpc(), txnBody.getPauseKey());
    }
}
