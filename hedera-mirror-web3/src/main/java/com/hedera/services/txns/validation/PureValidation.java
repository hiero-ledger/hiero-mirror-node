// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txns.validation;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.jproto.JKey;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.security.InvalidKeyException;

/**
 * Copied type from hedera-services.
 * <p>
 * Differences with the original:
 * 1. Removed methods which are not needed currently -queryableFileStatus, queryableAccountOrContractStatus,
 * queryableAccountStatus, internalQueryableAccountStatus, queryableContractStatus, queryableContractStatus,
 * chronologyStatus, asCoercedInstant, isValidStakedId
 */
public final class PureValidation {
    private PureValidation() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static ResponseCodeEnum checkKey(final Key key, final ResponseCodeEnum failure) {
        try {
            final var fcKey = JKey.mapKey(key);
            if (!fcKey.isValid()) {
                return failure;
            }
            return OK;
        } catch (InvalidKeyException e) {
            return failure;
        }
    }
}
