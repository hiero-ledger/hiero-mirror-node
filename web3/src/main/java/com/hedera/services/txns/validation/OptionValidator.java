// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txns.validation;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import org.hiero.mirror.web3.evm.store.Store;

/**
 * Copied Logic type from hedera-services. Unnecessary methods are deleted.
 */
public interface OptionValidator {

    ResponseCodeEnum nftMetadataCheck(byte[] metadata);

    ResponseCodeEnum maxBatchSizeMintCheck(int length);

    ResponseCodeEnum maxBatchSizeBurnCheck(int length);

    boolean isValidExpiry(final Timestamp expiry);

    public ResponseCodeEnum expiryStatusGiven(final Store store, final AccountID id);

    ResponseCodeEnum memoCheck(String cand);

    ResponseCodeEnum rawMemoCheck(byte[] cand);

    ResponseCodeEnum rawMemoCheck(byte[] cand, boolean hasZeroByte);

    ResponseCodeEnum tokenNameCheck(String name);

    ResponseCodeEnum tokenSymbolCheck(String symbol);

    default boolean isValidAutoRenewPeriod(final long len) {
        return isValidAutoRenewPeriod(Duration.newBuilder().setSeconds(len).build());
    }

    boolean isValidAutoRenewPeriod(Duration autoRenewPeriod);
}
