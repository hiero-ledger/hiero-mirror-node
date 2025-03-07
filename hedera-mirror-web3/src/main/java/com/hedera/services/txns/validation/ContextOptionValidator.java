// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txns.validation;

import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_TOKEN_NAME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_TOKEN_SYMBOL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NAME_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_SYMBOL_TOO_LONG;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.time.Instant;
import org.apache.commons.codec.binary.StringUtils;
import org.bouncycastle.util.Arrays;

/**
 * Copied Logic type from hedera-services. Unnecessary methods are deleted.
 */
public class ContextOptionValidator implements OptionValidator {

    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    public ContextOptionValidator(final MirrorNodeEvmProperties mirrorNodeEvmProperties) {
        this.mirrorNodeEvmProperties = mirrorNodeEvmProperties;
    }

    public static ResponseCodeEnum batchSizeCheck(final int length, final int limit) {
        return lengthCheck(length, limit, ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED);
    }

    private static ResponseCodeEnum lengthCheck(final long length, final long limit, final ResponseCodeEnum onFailure) {
        if (length > limit) {
            return onFailure;
        }
        return OK;
    }

    @Override
    public ResponseCodeEnum maxBatchSizeBurnCheck(final int length) {
        return batchSizeCheck(length, mirrorNodeEvmProperties.getMaxBatchSizeBurn());
    }

    @Override
    public ResponseCodeEnum maxBatchSizeMintCheck(final int length) {
        return batchSizeCheck(length, mirrorNodeEvmProperties.getMaxBatchSizeMint());
    }

    @Override
    public ResponseCodeEnum nftMetadataCheck(final byte[] metadata) {
        return lengthCheck(
                metadata.length, mirrorNodeEvmProperties.getMaxNftMetadataBytes(), ResponseCodeEnum.METADATA_TOO_LONG);
    }

    @Override
    public boolean isValidExpiry(Timestamp expiry) {
        final var now = Instant.now();
        final var then = Instant.ofEpochSecond(expiry.getSeconds(), expiry.getNanos());
        return then.isAfter(now);
    }

    @Override
    public boolean isValidAutoRenewPeriod(final Duration autoRenewPeriod) {
        final long duration = autoRenewPeriod.getSeconds();

        return duration >= mirrorNodeEvmProperties.getMinAutoRenewDuration()
                && duration <= mirrorNodeEvmProperties.getMaxAutoRenewDuration();
    }

    @Override
    public ResponseCodeEnum expiryStatusGiven(final Store store, final AccountID id) {
        var account = store.getAccount(asTypedEvmAddress(id), OnMissing.THROW);
        if (!mirrorNodeEvmProperties.shouldAutoRenewSomeEntityType()) {
            return OK;
        }
        final var balance = account.getBalance();
        if (balance > 0) {
            return OK;
        }
        final var isDetached = (account.getExpiry() < System.currentTimeMillis() / 1000);
        if (!isDetached) {
            return OK;
        }
        final var isContract = account.isSmartContract();
        return expiryStatusForNominallyDetached(isContract);
    }

    public ResponseCodeEnum expiryStatusGiven(final long balance, final boolean isDetached, final boolean isContract) {
        if (balance > 0 || !isDetached) {
            return OK;
        }
        return expiryStatusForNominallyDetached(isContract);
    }

    @Override
    public ResponseCodeEnum tokenSymbolCheck(final String symbol) {
        return tokenStringCheck(
                symbol,
                mirrorNodeEvmProperties.getMaxTokenSymbolUtf8Bytes(),
                MISSING_TOKEN_SYMBOL,
                TOKEN_SYMBOL_TOO_LONG);
    }

    @Override
    public ResponseCodeEnum tokenNameCheck(final String name) {
        return tokenStringCheck(
                name, mirrorNodeEvmProperties.getMaxTokenNameUtf8Bytes(), MISSING_TOKEN_NAME, TOKEN_NAME_TOO_LONG);
    }

    private ResponseCodeEnum tokenStringCheck(
            final String s, final int maxLen, final ResponseCodeEnum onMissing, final ResponseCodeEnum onTooLong) {
        final int numUtf8Bytes = StringUtils.getBytesUtf8(s).length;
        if (numUtf8Bytes == 0) {
            return onMissing;
        }
        if (numUtf8Bytes > maxLen) {
            return onTooLong;
        }
        if (s.contains("\u0000")) {
            return INVALID_ZERO_BYTE_IN_STRING;
        }
        return OK;
    }

    @Override
    public ResponseCodeEnum memoCheck(final String cand) {
        return rawMemoCheck(StringUtils.getBytesUtf8(cand));
    }

    @Override
    public ResponseCodeEnum rawMemoCheck(final byte[] utf8Cand) {
        return rawMemoCheck(utf8Cand, Arrays.contains(utf8Cand, (byte) 0));
    }

    @Override
    public ResponseCodeEnum rawMemoCheck(final byte[] utf8Cand, final boolean hasZeroByte) {
        if (utf8Cand.length > mirrorNodeEvmProperties.getMaxMemoUtf8Bytes()) {
            return MEMO_TOO_LONG;
        } else if (hasZeroByte) {
            return INVALID_ZERO_BYTE_IN_STRING;
        } else {
            return OK;
        }
    }

    private ResponseCodeEnum expiryStatusForNominallyDetached(final boolean isContract) {
        if (isExpiryDisabled(isContract)) {
            return OK;
        }
        return isContract ? CONTRACT_EXPIRED_AND_PENDING_REMOVAL : ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
    }

    private boolean isExpiryDisabled(final boolean isContract) {
        return (isContract && !mirrorNodeEvmProperties.shouldAutoRenewContracts())
                || (!isContract && !mirrorNodeEvmProperties.shouldAutoRenewAccounts());
    }
}
