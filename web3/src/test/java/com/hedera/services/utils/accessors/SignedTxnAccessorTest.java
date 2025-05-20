// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.utils.accessors;

import static com.hedera.services.fees.usage.token.TokenOpsUsageUtilsTest.A_COMPLEX_KEY;
import static com.hedera.services.fees.usage.token.TokenOpsUsageUtilsTest.A_KEY_LIST;
import static com.hedera.services.fees.usage.token.TokenOpsUsageUtilsTest.A_THRESHOLD_KEY;
import static com.hedera.services.fees.usage.token.TokenOpsUsageUtilsTest.B_COMPLEX_KEY;
import static com.hedera.services.fees.usage.token.TokenOpsUsageUtilsTest.C_COMPLEX_KEY;
import static com.hedera.services.utils.IdUtils.asAccount;
import static com.hedera.services.utils.IdUtils.asToken;
import static com.hedera.services.utils.TxnUtils.buildTransactionFrom;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.Int32Value;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.StringValue;
import com.hedera.services.utils.IdUtils;
import com.hedera.services.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TokenPauseTransactionBody;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenUnpauseTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionOrBuilder;
import com.hederahashgraph.api.proto.java.TransferList;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.hiero.mirror.web3.evm.account.MirrorEvmContractAliases;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class SignedTxnAccessorTest {
    private static final String MEMO = "Eternal sunshine of the spotless mind";
    private static final String ZERO_BYTE_MEMO = "Eternal s\u0000nshine of the spotless mind";
    private static final byte[] MEMO_UTF_8_BYTES = MEMO.getBytes();
    private static final byte[] ZERO_BYTE_MEMO_UTF_8_BYTES = ZERO_BYTE_MEMO.getBytes();

    private static final AccountID SPENDER_1 = asAccount("0.0.1000");
    private static final AccountID OWNER = asAccount("0.0.1001");
    private static final TokenID TOKEN_1 = asToken("0.0.2000");
    private static final TokenID TOKEN_2 = asToken("0.0.3000");
    private static final CryptoAllowance CRYPTO_ALLOWANCE_1 =
            CryptoAllowance.newBuilder().setSpender(SPENDER_1).setAmount(10L).build();
    private static final TokenAllowance TOKEN_ALLOWANCE_1 = TokenAllowance.newBuilder()
            .setSpender(SPENDER_1)
            .setAmount(10L)
            .setTokenId(TOKEN_1)
            .build();
    private static final NftAllowance NFT_ALLOWANCE_1 = NftAllowance.newBuilder()
            .setSpender(SPENDER_1)
            .setTokenId(TOKEN_2)
            .setApprovedForAll(BoolValue.of(false))
            .addAllSerialNumbers(List.of(1L, 10L))
            .build();

    private static final NftRemoveAllowance NFT_REMOVE_ALLOWANCE = NftRemoveAllowance.newBuilder()
            .setOwner(OWNER)
            .setTokenId(TOKEN_2)
            .addAllSerialNumbers(List.of(1L, 10L))
            .build();

    private static final SignatureMap EXPECTED_MAP = SignatureMap.newBuilder()
            .addSigPair(SignaturePair.newBuilder()
                    .setPubKeyPrefix(ByteString.copyFromUtf8("f"))
                    .setEd25519(ByteString.copyFromUtf8("irst")))
            .addSigPair(SignaturePair.newBuilder()
                    .setPubKeyPrefix(ByteString.copyFromUtf8("s"))
                    .setEd25519(ByteString.copyFromUtf8("econd")))
            .build();

    @Test
    void uncheckedPropagatesIaeOnNonsense() {
        final var nonsenseTxn = buildTransactionFrom(ByteString.copyFromUtf8("NONSENSE"));

        assertThrows(IllegalArgumentException.class, () -> SignedTxnAccessor.uncheckedFrom(nonsenseTxn));
    }

    @Test
    void parsesLegacyCorrectly() throws Exception {
        final MirrorEvmContractAliases aliasManager = mock(MirrorEvmContractAliases.class);
        given(aliasManager.resolveForEvm(any())).willReturn(Address.ZERO);

        final long offeredFee = 100_000_000L;
        var xferNoAliases = RequestBuilderUtils.getCryptoTransferRequest(
                1234l,
                0l,
                0l,
                3l,
                0l,
                0l,
                offeredFee,
                Timestamp.getDefaultInstance(),
                Duration.getDefaultInstance(),
                false,
                ZERO_BYTE_MEMO,
                5678l,
                -70000l,
                5679l,
                70000l);
        xferNoAliases = xferNoAliases.toBuilder().setSigMap(EXPECTED_MAP).build();
        final var body = TransactionBody.parseFrom(extractTransactionBodyByteString(xferNoAliases));

        final var signedTransaction = TxnUtils.signedTransactionFrom(body, EXPECTED_MAP);
        final var newTransaction = buildTransactionFrom(signedTransaction.toByteString());
        var accessor = SignedTxnAccessor.uncheckedFrom(newTransaction);
        final var txnUsageMeta = accessor.baseUsageMeta();

        assertEquals(newTransaction, accessor.getSignedTxnWrapper());
        assertEquals(body, accessor.getTxn());
        assertArrayEquals(body.toByteArray(), accessor.getTxnBytes());
        assertEquals(body.getTransactionID(), accessor.getTxnId());
        assertEquals(1234l, accessor.getPayer().getAccountNum());
        assertEquals(HederaFunctionality.CryptoTransfer, accessor.getFunction());
        assertArrayEquals(noThrowSha384HashOf(signedTransaction.toByteArray()), accessor.getHash());
        assertEquals(EXPECTED_MAP, accessor.getSigMap());
        assertArrayEquals(ZERO_BYTE_MEMO_UTF_8_BYTES, accessor.getMemoUtf8Bytes());
        assertEquals(ZERO_BYTE_MEMO, accessor.getMemo());
        assertEquals(MEMO_UTF_8_BYTES.length, txnUsageMeta.memoUtf8Bytes());
    }

    @Test
    void detectsCommonTokenBurnSubtypeFromGrpcSyntax() {
        final var op = TokenBurnTransactionBody.newBuilder().setAmount(1_234).build();
        final var txn = buildTransactionFrom(
                TransactionBody.newBuilder().setTokenBurn(op).build());

        final var subject = SignedTxnAccessor.uncheckedFrom(txn);

        assertEquals(TOKEN_FUNGIBLE_COMMON, subject.getSubType());
    }

    @Test
    void detectsUniqueTokenBurnSubtypeFromGrpcSyntax() {
        final var op = TokenBurnTransactionBody.newBuilder()
                .addAllSerialNumbers(List.of(1L, 2L, 3L))
                .build();
        final var txn = buildTransactionFrom(
                TransactionBody.newBuilder().setTokenBurn(op).build());

        final var subject = SignedTxnAccessor.uncheckedFrom(txn);

        assertEquals(TOKEN_NON_FUNGIBLE_UNIQUE, subject.getSubType());
    }

    @Test
    void detectsCommonTokenMintSubtypeFromGrpcSyntax() {
        final var op = TokenMintTransactionBody.newBuilder().setAmount(1_234).build();
        final var txn = buildTransactionFrom(
                TransactionBody.newBuilder().setTokenMint(op).build());

        final var subject = SignedTxnAccessor.uncheckedFrom(txn);

        assertEquals(TOKEN_FUNGIBLE_COMMON, subject.getSubType());
    }

    @Test
    void detectsUniqueTokenMintSubtypeFromGrpcSyntax() {
        final var op = TokenMintTransactionBody.newBuilder()
                .addAllMetadata(List.of(ByteString.copyFromUtf8("STANDARD")))
                .build();
        final var txn = buildTransactionFrom(
                TransactionBody.newBuilder().setTokenMint(op).build());

        final var subject = SignedTxnAccessor.uncheckedFrom(txn);

        assertEquals(TOKEN_NON_FUNGIBLE_UNIQUE, subject.getSubType());
    }

    @Test
    void fetchesSubTypeAsExpected() throws InvalidProtocolBufferException {
        final var nftTransfers = TokenTransferList.newBuilder()
                .setToken(AN_ID)
                .addNftTransfers(NftTransfer.newBuilder()
                        .setSenderAccountID(A)
                        .setReceiverAccountID(B)
                        .setSerialNumber(1))
                .build();
        final var fungibleTokenXfers = TokenTransferList.newBuilder()
                .setToken(ANOTHER_ID)
                .addAllTransfers(List.of(adjustFrom(A, -50), adjustFrom(B, 25), adjustFrom(C, 25)))
                .build();

        var txn = buildTokenTransferTxn(nftTransfers);
        var subject = SignedTxnAccessor.from(txn.toByteArray());
        assertEquals(
                SubType.TOKEN_NON_FUNGIBLE_UNIQUE, subject.availXferUsageMeta().getSubType());
        assertEquals(subject.availXferUsageMeta().getSubType(), subject.getSubType());

        // set customFee
        var xferUsageMeta = subject.availXferUsageMeta();
        xferUsageMeta.setCustomFeeHbarTransfers(1);
        assertEquals(SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES, subject.getSubType());
        xferUsageMeta.setCustomFeeHbarTransfers(0);

        txn = buildTokenTransferTxn(fungibleTokenXfers);
        subject = SignedTxnAccessor.from(txn.toByteArray(), txn);
        assertEquals(TOKEN_FUNGIBLE_COMMON, subject.availXferUsageMeta().getSubType());
        assertEquals(subject.availXferUsageMeta().getSubType(), subject.getSubType());

        // set customFee
        xferUsageMeta = subject.availXferUsageMeta();
        xferUsageMeta.setCustomFeeTokenTransfers(1);
        assertEquals(SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES, subject.getSubType());
        xferUsageMeta.setCustomFeeTokenTransfers(0);

        txn = buildDefaultCryptoCreateTxn();
        subject = SignedTxnAccessor.from(txn.toByteArray(), txn);
        assertEquals(SubType.DEFAULT, subject.getSubType());
    }

    @Test
    void understandsFullXferUsageIncTokens() {
        final var txn = buildTransactionFrom(tokenXfers());
        final var subject = SignedTxnAccessor.uncheckedFrom(txn);

        final var xferMeta = subject.availXferUsageMeta();

        assertEquals(1, xferMeta.getTokenMultiplier());
        assertEquals(3, xferMeta.getNumTokensInvolved());
        assertEquals(7, xferMeta.getNumFungibleTokenTransfers());
    }

    @Test
    void rejectsRequestForMetaIfNotAvail() {
        final var txn = buildDefaultCryptoCreateTxn();

        final var subject = SignedTxnAccessor.uncheckedFrom(txn);

        assertEquals(SubType.DEFAULT, subject.getSubType());
        assertThrows(IllegalStateException.class, subject::availXferUsageMeta);
    }

    @Test
    void parseNewTransactionCorrectly() throws Exception {
        final var transaction = RequestBuilderUtils.getCryptoTransferRequest(
                1234l,
                0l,
                0l,
                3l,
                0l,
                0l,
                100_000_000l,
                Timestamp.getDefaultInstance(),
                Duration.getDefaultInstance(),
                false,
                MEMO,
                5678l,
                -70000l,
                5679l,
                70000l);
        final var body = extractTransactionBody(transaction);
        final var signedTransaction = TxnUtils.signedTransactionFrom(body, EXPECTED_MAP);
        final var newTransaction = buildTransactionFrom(signedTransaction.toByteString());
        final var accessor = SignedTxnAccessor.uncheckedFrom(newTransaction);

        assertEquals(newTransaction, accessor.getSignedTxnWrapper());
        assertEquals(body, accessor.getTxn());
        assertArrayEquals(body.toByteArray(), accessor.getTxnBytes());
        assertEquals(body.getTransactionID(), accessor.getTxnId());
        assertEquals(1234l, accessor.getPayer().getAccountNum());
        assertEquals(HederaFunctionality.CryptoTransfer, accessor.getFunction());
        assertArrayEquals(noThrowSha384HashOf(signedTransaction.toByteArray()), accessor.getHash());
        assertEquals(EXPECTED_MAP, accessor.getSigMap());
        assertArrayEquals(MEMO_UTF_8_BYTES, accessor.getMemoUtf8Bytes());
        assertEquals(MEMO, accessor.getMemo());
    }

    @Test
    void registersNoneOnMalformedCreation() throws InvalidProtocolBufferException {
        final var xferWithTopLevelBodyBytes = RequestBuilderUtils.getCryptoTransferRequest(
                1234l,
                0l,
                0l,
                3l,
                0l,
                0l,
                100_000_000l,
                Timestamp.getDefaultInstance(),
                Duration.getDefaultInstance(),
                false,
                "test memo",
                5678l,
                -70000l,
                5679l,
                70000l);
        final var signedTxn = SignedTransaction.parseFrom(xferWithTopLevelBodyBytes.getSignedTransactionBytes());
        final var body = TransactionBody.parseFrom(signedTxn.getBodyBytes());

        final var confusedTxn = Transaction.parseFrom(body.toByteArray());

        final var confusedAccessor = SignedTxnAccessor.uncheckedFrom(confusedTxn);

        assertEquals(HederaFunctionality.NONE, confusedAccessor.getFunction());
    }

    @Test
    void setTokenCreateUsageMetaWorks() {
        final var txn = signedTokenCreateTxn();
        final var accessor = SignedTxnAccessor.uncheckedFrom(txn);
        final var spanMapAccessor = accessor.getSpanMapAccessor();

        final var expandedMeta = spanMapAccessor.getTokenCreateMeta(accessor);

        assertEquals(0, expandedMeta.getNftsTransfers());
        assertEquals(1, expandedMeta.getFungibleNumTransfers());
        assertEquals(1, expandedMeta.getNumTokens());
        assertEquals(1070, expandedMeta.getBaseSize());
        assertEquals(TOKEN_FUNGIBLE_COMMON, accessor.getSubType());
    }

    @Test
    void setTokenPauseUsageMetaWorks() {
        final var op = TokenPauseTransactionBody.newBuilder()
                .setToken(TokenID.newBuilder().setTokenNum(123).build());
        final var txnBody = TransactionBody.newBuilder()
                .setTransactionID(TransactionID.newBuilder()
                        .setTransactionValidStart(Timestamp.newBuilder().setSeconds(NOW)))
                .setTokenPause(op)
                .build();
        final var txn = buildTransactionFrom(txnBody);
        final var accessor = SignedTxnAccessor.uncheckedFrom(txn);
        final var spanMapAccessor = accessor.getSpanMapAccessor();

        final var expandedMeta = spanMapAccessor.getTokenPauseMeta(accessor);

        assertEquals(24, expandedMeta.getBpt());
    }

    @Test
    void setTokenUnpauseUsageMetaWorks() {
        final var op = TokenUnpauseTransactionBody.newBuilder()
                .setToken(TokenID.newBuilder().setTokenNum(123).build());
        final var txnBody = TransactionBody.newBuilder()
                .setTransactionID(TransactionID.newBuilder()
                        .setTransactionValidStart(Timestamp.newBuilder().setSeconds(NOW)))
                .setTokenUnpause(op)
                .build();
        final var txn = buildTransactionFrom(txnBody);
        final var accessor = SignedTxnAccessor.uncheckedFrom(txn);
        final var spanMapAccessor = accessor.getSpanMapAccessor();

        final var expandedMeta = spanMapAccessor.getTokenUnpauseMeta(accessor);

        assertEquals(24, expandedMeta.getBpt());
    }

    @Test
    void setCryptoCreateUsageMetaWorks() {
        final var txn = signedCryptoCreateTxn();
        final var accessor = SignedTxnAccessor.uncheckedFrom(txn);
        final var spanMapAccessor = accessor.getSpanMapAccessor();

        final var expandedMeta = spanMapAccessor.getCryptoCreateMeta(accessor);

        assertEquals(137, expandedMeta.getBaseSize());
        assertEquals(AUTO_RENEW_PERIOD, expandedMeta.getLifeTime());
        assertEquals(10, expandedMeta.getMaxAutomaticAssociations());
    }

    @Test
    void setCryptoUpdateUsageMetaWorks() {
        final var txn = signedCryptoUpdateTxn();
        final var accessor = SignedTxnAccessor.uncheckedFrom(txn);
        final var spanMapAccessor = accessor.getSpanMapAccessor();

        final var expandedMeta = spanMapAccessor.getCryptoUpdateMeta(accessor);

        assertEquals(100, expandedMeta.getKeyBytesUsed());
        assertEquals(197, expandedMeta.getMsgBytesUsed());
        assertEquals(NOW, expandedMeta.getEffectiveNow());
        assertEquals(NOW + AUTO_RENEW_PERIOD, expandedMeta.getExpiry());
        assertEquals(MEMO.getBytes().length, expandedMeta.getMemoSize());
        assertEquals(25, expandedMeta.getMaxAutomaticAssociations());
        assertTrue(expandedMeta.hasProxy());
    }

    @Test
    void setCryptoApproveUsageMetaWorks() {
        final var txn = signedCryptoApproveTxn();
        final var accessor = SignedTxnAccessor.uncheckedFrom(txn);
        final var spanMapAccessor = accessor.getSpanMapAccessor();

        final var expandedMeta = spanMapAccessor.getCryptoApproveMeta(accessor);

        assertEquals(128, expandedMeta.getMsgBytesUsed());
        assertEquals(NOW, expandedMeta.getEffectiveNow());
    }

    @Test
    void setCryptoDeleteAllowanceUsageMetaWorks() {
        final var txn = signedCryptoDeleteAllowanceTxn();
        final var accessor = SignedTxnAccessor.uncheckedFrom(txn);
        final var spanMapAccessor = accessor.getSpanMapAccessor();

        final var expandedMeta = spanMapAccessor.getCryptoDeleteAllowanceMeta(accessor);

        assertEquals(64, expandedMeta.getMsgBytesUsed());
        assertEquals(NOW, expandedMeta.getEffectiveNow());
    }

    private Transaction signedCryptoCreateTxn() {
        return buildTransactionFrom(cryptoCreateOp());
    }

    private Transaction signedCryptoUpdateTxn() {
        return buildTransactionFrom(cryptoUpdateOp());
    }

    private Transaction signedCryptoApproveTxn() {
        return buildTransactionFrom(cryptoApproveOp());
    }

    private Transaction signedCryptoDeleteAllowanceTxn() {
        return buildTransactionFrom(cryptoDeleteAllowanceOp());
    }

    private TransactionBody cryptoCreateOp() {
        final var op = CryptoCreateTransactionBody.newBuilder()
                .setMemo(MEMO)
                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(AUTO_RENEW_PERIOD))
                .setKey(ADMIN_KEY)
                .setMaxAutomaticTokenAssociations(10);
        return TransactionBody.newBuilder()
                .setTransactionID(TransactionID.newBuilder()
                        .setTransactionValidStart(Timestamp.newBuilder().setSeconds(NOW)))
                .setCryptoCreateAccount(op)
                .build();
    }

    private TransactionBody cryptoUpdateOp() {
        final var op = CryptoUpdateTransactionBody.newBuilder()
                .setExpirationTime(Timestamp.newBuilder().setSeconds(NOW + AUTO_RENEW_PERIOD))
                .setProxyAccountID(AUTO_RENEW_ACCOUNT)
                .setMemo(StringValue.newBuilder().setValue(MEMO))
                .setMaxAutomaticTokenAssociations(Int32Value.of(25))
                .setKey(ADMIN_KEY);
        return TransactionBody.newBuilder()
                .setTransactionID(TransactionID.newBuilder()
                        .setTransactionValidStart(Timestamp.newBuilder().setSeconds(NOW)))
                .setCryptoUpdateAccount(op)
                .build();
    }

    private TransactionBody cryptoApproveOp() {
        final var op = CryptoApproveAllowanceTransactionBody.newBuilder()
                .addAllCryptoAllowances(List.of(CRYPTO_ALLOWANCE_1))
                .addAllTokenAllowances(List.of(TOKEN_ALLOWANCE_1))
                .addAllNftAllowances(List.of(NFT_ALLOWANCE_1))
                .build();
        return TransactionBody.newBuilder()
                .setTransactionID(TransactionID.newBuilder()
                        .setTransactionValidStart(Timestamp.newBuilder().setSeconds(NOW)))
                .setCryptoApproveAllowance(op)
                .build();
    }

    private TransactionBody cryptoDeleteAllowanceOp() {
        final var op = CryptoDeleteAllowanceTransactionBody.newBuilder()
                .addAllNftAllowances(List.of(NFT_REMOVE_ALLOWANCE))
                .build();
        return TransactionBody.newBuilder()
                .setTransactionID(TransactionID.newBuilder()
                        .setTransactionValidStart(Timestamp.newBuilder().setSeconds(NOW)))
                .setCryptoDeleteAllowance(op)
                .build();
    }

    private Transaction buildTokenTransferTxn(final TokenTransferList tokenTransferList) {
        final var op = CryptoTransferTransactionBody.newBuilder()
                .addTokenTransfers(tokenTransferList)
                .build();
        final var txnBody = TransactionBody.newBuilder()
                .setMemo(MEMO)
                .setTransactionID(TransactionID.newBuilder()
                        .setTransactionValidStart(Timestamp.newBuilder().setSeconds(NOW)))
                .setCryptoTransfer(op)
                .build();

        return buildTransactionFrom(txnBody);
    }

    private Transaction buildDefaultCryptoCreateTxn() {
        final var txnBody = TransactionBody.newBuilder()
                .setCryptoCreateAccount(CryptoCreateTransactionBody.getDefaultInstance())
                .build();

        return buildTransactionFrom(txnBody);
    }

    private TransactionBody tokenXfers() {
        final var hbarAdjusts = TransferList.newBuilder()
                .addAccountAmounts(adjustFrom(A, -100))
                .addAccountAmounts(adjustFrom(B, 50))
                .addAccountAmounts(adjustFrom(C, 50))
                .build();
        final var op = CryptoTransferTransactionBody.newBuilder()
                .setTransfers(hbarAdjusts)
                .addTokenTransfers(TokenTransferList.newBuilder()
                        .setToken(ANOTHER_ID)
                        .addAllTransfers(List.of(adjustFrom(A, -50), adjustFrom(B, 25), adjustFrom(C, 25))))
                .addTokenTransfers(TokenTransferList.newBuilder()
                        .setToken(AN_ID)
                        .addAllTransfers(List.of(adjustFrom(B, -100), adjustFrom(C, 100))))
                .addTokenTransfers(TokenTransferList.newBuilder()
                        .setToken(YET_ANOTHER_ID)
                        .addAllTransfers(List.of(adjustFrom(A, -15), adjustFrom(B, 15))))
                .build();

        return TransactionBody.newBuilder()
                .setMemo(MEMO)
                .setTransactionID(TransactionID.newBuilder()
                        .setTransactionValidStart(Timestamp.newBuilder().setSeconds(NOW)))
                .setCryptoTransfer(op)
                .build();
    }

    private AccountAmount adjustFrom(final AccountID account, final long amount) {
        return AccountAmount.newBuilder()
                .setAmount(amount)
                .setAccountID(account)
                .build();
    }

    private Transaction signedTokenCreateTxn() {
        return buildTransactionFrom(givenAutoRenewBasedOp());
    }

    private TransactionBody givenAutoRenewBasedOp() {
        final var op = TokenCreateTransactionBody.newBuilder()
                .setAutoRenewAccount(AUTO_RENEW_ACCOUNT)
                .setMemo(MEMO)
                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(AUTO_RENEW_PERIOD))
                .setSymbol(SYMBOL)
                .setName(NAME)
                .setKycKey(KYC_KEY)
                .setAdminKey(ADMIN_KEY)
                .setFreezeKey(FREEZE_KEY)
                .setSupplyKey(SUPPLY_KEY)
                .setWipeKey(WIPE_KEY)
                .setInitialSupply(1);
        return TransactionBody.newBuilder()
                .setTransactionID(TransactionID.newBuilder()
                        .setTransactionValidStart(Timestamp.newBuilder().setSeconds(NOW)))
                .setTokenCreation(op)
                .build();
    }

    private static final Key KYC_KEY = A_COMPLEX_KEY;
    private static final Key ADMIN_KEY = A_THRESHOLD_KEY;
    private static final Key FREEZE_KEY = A_KEY_LIST;
    private static final Key SUPPLY_KEY = B_COMPLEX_KEY;
    private static final Key WIPE_KEY = C_COMPLEX_KEY;
    private static final long AUTO_RENEW_PERIOD = 1_234_567L;
    private static final String SYMBOL = "ABCDEFGH";
    private static final String NAME = "WhyWhyWHy";
    private static final AccountID AUTO_RENEW_ACCOUNT = IdUtils.asAccount("0.0.75231");

    private static final long NOW = 1_234_567L;
    private static final AccountID A = asAccount("1.2.3");
    private static final AccountID B = asAccount("2.3.4");
    private static final AccountID C = asAccount("3.4.5");
    private static final TokenID AN_ID = IdUtils.asToken("0.0.75231");
    private static final TokenID ANOTHER_ID = IdUtils.asToken("0.0.75232");
    private static final TokenID YET_ANOTHER_ID = IdUtils.asToken("0.0.75233");

    static ByteString extractTransactionBodyByteString(final TransactionOrBuilder transaction)
            throws InvalidProtocolBufferException {
        final var signedTransactionBytes = transaction.getSignedTransactionBytes();
        if (!signedTransactionBytes.isEmpty()) {
            return SignedTransaction.parseFrom(signedTransactionBytes).getBodyBytes();
        }
        return transaction.getBodyBytes();
    }

    static byte[] noThrowSha384HashOf(final byte[] byteArray) {
        try {
            return MessageDigest.getInstance("SHA-384").digest(byteArray);
        } catch (final NoSuchAlgorithmException fatal) {
            throw new IllegalStateException(fatal);
        }
    }

    static TransactionBody extractTransactionBody(final TransactionOrBuilder transaction)
            throws InvalidProtocolBufferException {
        return TransactionBody.parseFrom(extractTransactionBodyByteString(transaction));
    }
}
