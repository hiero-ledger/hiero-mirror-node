// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.utils.accessors;

import static com.hedera.services.fees.usage.token.TokenOpsUsageUtils.TOKEN_OPS_USAGE_UTILS;
import static com.hedera.services.utils.MiscUtils.FUNCTION_EXTRACTOR;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoApproveAllowance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDeleteAllowance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenPause;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnfreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnpause;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.hapi.fees.usage.BaseTransactionMeta;
import com.hedera.services.hapi.fees.usage.SigUsage;
import com.hedera.services.hapi.fees.usage.crypto.CryptoApproveAllowanceMeta;
import com.hedera.services.hapi.fees.usage.crypto.CryptoCreateMeta;
import com.hedera.services.hapi.fees.usage.crypto.CryptoDeleteAllowanceMeta;
import com.hedera.services.hapi.fees.usage.crypto.CryptoTransferMeta;
import com.hedera.services.hapi.fees.usage.crypto.CryptoUpdateMeta;
import com.hedera.services.txns.span.ExpandHandleSpanMapAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *  Copied Logic type from hedera-services. Differences with the original:
 *  1. setTokenWipeUsageMeta is added to setOpUsageMeta
 */
public class SignedTxnAccessor implements TxnAccessor {

    private static final String ACCESSOR_LITERAL = " accessor";
    private HederaFunctionality function;
    private Map<String, Object> spanMap = new HashMap<>();
    private CryptoTransferMeta xferUsageMeta;
    private BaseTransactionMeta txnUsageMeta;
    private static final ExpandHandleSpanMapAccessor SPAN_MAP_ACCESSOR = new ExpandHandleSpanMapAccessor();
    private final int sigMapSize;
    private final int numSigPairs;
    private final byte[] hash;
    private final byte[] txnBytes;
    private final byte[] utf8MemoBytes;
    private final String memo;
    private final Transaction signedTxnWrapper;
    private final SignatureMap sigMap;
    private final TransactionID txnId;
    private final TransactionBody txn;
    private AccountID payer;
    private static final Logger log = LogManager.getLogger(SignedTxnAccessor.class);

    public static SignedTxnAccessor uncheckedFrom(final Transaction validSignedTxn) {
        try {
            return SignedTxnAccessor.from(validSignedTxn.toByteArray());
        } catch (final Exception illegal) {
            log.warn("Unexpected use of factory with invalid gRPC transaction", illegal);
            throw new IllegalArgumentException("Argument 'validSignedTxn' must be a valid signed txn");
        }
    }

    public static SignedTxnAccessor from(final byte[] signedTxnWrapperBytes) throws InvalidProtocolBufferException {
        return new SignedTxnAccessor(signedTxnWrapperBytes, null);
    }

    public static SignedTxnAccessor from(final byte[] signedTxnWrapperBytes, final Transaction signedTxnWrapper)
            throws InvalidProtocolBufferException {
        return new SignedTxnAccessor(signedTxnWrapperBytes, signedTxnWrapper);
    }

    @SuppressWarnings("deprecation")
    protected SignedTxnAccessor(final byte[] signedTxnWrapperBytes, @Nullable final Transaction transaction)
            throws InvalidProtocolBufferException {

        final Transaction txnWrapper;
        if (transaction != null) {
            txnWrapper = transaction;
        } else {
            txnWrapper = Transaction.parseFrom(signedTxnWrapperBytes);
        }
        this.signedTxnWrapper = txnWrapper;

        final var signedTxnBytes = signedTxnWrapper.getSignedTransactionBytes();
        if (signedTxnBytes.isEmpty()) {
            txnBytes = unwrapUnsafelyIfPossible(signedTxnWrapper.getBodyBytes());
            sigMap = signedTxnWrapper.getSigMap();
            hash = noThrowSha384HashOf(signedTxnWrapperBytes);
        } else {
            final var signedTxn = SignedTransaction.parseFrom(signedTxnBytes);
            txnBytes = unwrapUnsafelyIfPossible(signedTxn.getBodyBytes());
            sigMap = signedTxn.getSigMap();
            hash = noThrowSha384HashOf(unwrapUnsafelyIfPossible(signedTxnBytes));
        }

        txn = TransactionBody.parseFrom(txnBytes);
        // Note that the SignatureMap was parsed with either the top-level
        // Transaction or the SignedTransaction, so we've already checked
        // it for unknown fields either way; only still need to check the body
        memo = txn.getMemo();
        txnId = txn.getTransactionID();
        sigMapSize = sigMap.getSerializedSize();
        numSigPairs = sigMap.getSigPairCount();
        utf8MemoBytes = StringUtils.getBytesUtf8(memo);
        payer = getTxnId().getAccountID();

        getFunction();
        setBaseUsageMeta();
        setOpUsageMeta();
    }

    public static byte[] unwrapUnsafelyIfPossible(@NonNull final ByteString byteString) {
        return byteString.toByteArray();
    }

    public static byte[] noThrowSha384HashOf(final byte[] byteArray) {
        try {
            return MessageDigest.getInstance("SHA-384").digest(byteArray);
        } catch (final NoSuchAlgorithmException fatal) {
            throw new IllegalStateException(fatal);
        }
    }

    @Override
    public HederaFunctionality getFunction() {
        if (function == null) {
            function = FUNCTION_EXTRACTOR.apply(getTxn());
        }
        return function;
    }

    @Override
    public Transaction getSignedTxnWrapper() {
        return signedTxnWrapper;
    }

    @Override
    public TransactionBody getTxn() {
        return txn;
    }

    @Override
    public TransactionID getTxnId() {
        return txnId;
    }

    @Override
    public AccountID getPayer() {
        return payer;
    }

    @Override
    public SignatureMap getSigMap() {
        return sigMap;
    }

    @Override
    public byte[] getMemoUtf8Bytes() {
        return utf8MemoBytes;
    }

    @Override
    public byte[] getTxnBytes() {
        return txnBytes;
    }

    @Override
    public String getMemo() {
        return memo;
    }

    @Override
    public byte[] getHash() {
        return hash;
    }

    @Override
    public SigUsage usageGiven(final int numPayerKeys) {
        return new SigUsage(numSigPairs, sigMapSize, numPayerKeys);
    }

    @Override
    public Map<String, Object> getSpanMap() {
        return spanMap;
    }

    @Override
    public ExpandHandleSpanMapAccessor getSpanMapAccessor() {
        return SPAN_MAP_ACCESSOR;
    }

    @Override
    public BaseTransactionMeta baseUsageMeta() {
        return txnUsageMeta;
    }

    @Override
    public CryptoTransferMeta availXferUsageMeta() {
        if (function != CryptoTransfer) {
            throw new IllegalStateException("Cannot get CryptoTransfer metadata for a " + function + ACCESSOR_LITERAL);
        }
        return xferUsageMeta;
    }

    private void setBaseUsageMeta() {
        if (function == CryptoTransfer) {
            txnUsageMeta = new BaseTransactionMeta(
                    utf8MemoBytes.length, txn.getCryptoTransfer().getTransfers().getAccountAmountsCount());
        } else {
            txnUsageMeta = new BaseTransactionMeta(utf8MemoBytes.length, 0);
        }
    }

    /* This section should be deleted after custom accessors are complete */
    private void setOpUsageMeta() {
        if (function == CryptoTransfer) {
            setXferUsageMeta();
        } else if (function == TokenCreate) {
            setTokenCreateUsageMeta();
        } else if (function == TokenBurn) {
            setTokenBurnUsageMeta();
        } else if (function == TokenAccountWipe) {
            setTokenWipeUsageMeta();
        } else if (function == TokenFreezeAccount) {
            setTokenFreezeUsageMeta();
        } else if (function == TokenUnfreezeAccount) {
            setTokenUnfreezeUsageMeta();
        } else if (function == TokenPause) {
            setTokenPauseUsageMeta();
        } else if (function == TokenUnpause) {
            setTokenUnpauseUsageMeta();
        } else if (function == CryptoCreate) {
            setCryptoCreateUsageMeta();
        } else if (function == CryptoUpdate) {
            setCryptoUpdateUsageMeta();
        } else if (function == CryptoApproveAllowance) {
            setCryptoApproveUsageMeta();
        } else if (function == CryptoDeleteAllowance) {
            setCryptoDeleteAllowanceUsageMeta();
        }
    }

    private void setXferUsageMeta() {
        var totalTokensInvolved = 0;
        var totalTokenTransfers = 0;
        var numNftOwnershipChanges = 0;
        final var op = txn.getCryptoTransfer();
        for (final var tokenTransfers : op.getTokenTransfersList()) {
            totalTokensInvolved++;
            totalTokenTransfers += tokenTransfers.getTransfersCount();
            numNftOwnershipChanges += tokenTransfers.getNftTransfersCount();
        }
        xferUsageMeta = new CryptoTransferMeta(1, totalTokensInvolved, totalTokenTransfers, numNftOwnershipChanges);
    }

    private void setTokenWipeUsageMeta() {
        final var tokenWipeMeta = TOKEN_OPS_USAGE_UTILS.tokenWipeUsageFrom(txn);
        getSpanMapAccessor().setTokenWipeMeta(this, tokenWipeMeta);
    }

    private void setTokenCreateUsageMeta() {
        final var tokenCreateMeta = TOKEN_OPS_USAGE_UTILS.tokenCreateUsageFrom(txn);
        SPAN_MAP_ACCESSOR.setTokenCreateMeta(this, tokenCreateMeta);
    }

    private void setTokenBurnUsageMeta() {
        final var tokenBurnMeta = TOKEN_OPS_USAGE_UTILS.tokenBurnUsageFrom(txn);
        SPAN_MAP_ACCESSOR.setTokenBurnMeta(this, tokenBurnMeta);
    }

    private void setTokenFreezeUsageMeta() {
        final var tokenFreezeMeta = TOKEN_OPS_USAGE_UTILS.tokenFreezeUsageFrom();
        SPAN_MAP_ACCESSOR.setTokenFreezeMeta(this, tokenFreezeMeta);
    }

    private void setTokenUnfreezeUsageMeta() {
        final var tokenUnfreezeMeta = TOKEN_OPS_USAGE_UTILS.tokenUnfreezeUsageFrom();
        SPAN_MAP_ACCESSOR.setTokenUnfreezeMeta(this, tokenUnfreezeMeta);
    }

    private void setTokenPauseUsageMeta() {
        final var tokenPauseMeta = TOKEN_OPS_USAGE_UTILS.tokenPauseUsageFrom();
        SPAN_MAP_ACCESSOR.setTokenPauseMeta(this, tokenPauseMeta);
    }

    private void setTokenUnpauseUsageMeta() {
        final var tokenUnpauseMeta = TOKEN_OPS_USAGE_UTILS.tokenUnpauseUsageFrom();
        SPAN_MAP_ACCESSOR.setTokenUnpauseMeta(this, tokenUnpauseMeta);
    }

    private void setCryptoCreateUsageMeta() {
        final var cryptoCreateMeta = new CryptoCreateMeta(txn.getCryptoCreateAccount());
        SPAN_MAP_ACCESSOR.setCryptoCreateMeta(this, cryptoCreateMeta);
    }

    private void setCryptoUpdateUsageMeta() {
        final var cryptoUpdateMeta = new CryptoUpdateMeta(
                txn.getCryptoUpdateAccount(),
                txn.getTransactionID().getTransactionValidStart().getSeconds());
        SPAN_MAP_ACCESSOR.setCryptoUpdate(this, cryptoUpdateMeta);
    }

    private void setCryptoApproveUsageMeta() {
        final var cryptoApproveMeta = new CryptoApproveAllowanceMeta(
                txn.getCryptoApproveAllowance(),
                txn.getTransactionID().getTransactionValidStart().getSeconds());
        SPAN_MAP_ACCESSOR.setCryptoApproveMeta(this, cryptoApproveMeta);
    }

    private void setCryptoDeleteAllowanceUsageMeta() {
        final var cryptoDeleteAllowanceMeta = new CryptoDeleteAllowanceMeta(
                txn.getCryptoDeleteAllowance(),
                txn.getTransactionID().getTransactionValidStart().getSeconds());
        SPAN_MAP_ACCESSOR.setCryptoDeleteAllowanceMeta(this, cryptoDeleteAllowanceMeta);
    }

    @Override
    public SubType getSubType() {
        if (function == CryptoTransfer) {
            return xferUsageMeta.getSubType();
        } else if (function == TokenCreate) {
            return SPAN_MAP_ACCESSOR.getTokenCreateMeta(this).getSubType();
        } else if (function == TokenMint) {
            final var op = getTxn().getTokenMint();
            return op.getMetadataCount() > 0 ? TOKEN_NON_FUNGIBLE_UNIQUE : TOKEN_FUNGIBLE_COMMON;
        } else if (function == TokenBurn) {
            return SPAN_MAP_ACCESSOR.getTokenBurnMeta(this).getSubType();
        } else if (function == TokenAccountWipe) {
            return SPAN_MAP_ACCESSOR.getTokenWipeMeta(this).getSubType();
        }
        return SubType.DEFAULT;
    }
}
