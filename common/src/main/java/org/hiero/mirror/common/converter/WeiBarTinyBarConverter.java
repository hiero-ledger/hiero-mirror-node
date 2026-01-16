// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import java.math.BigInteger;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("java:S6548")
public class WeiBarTinyBarConverter {

    public static final WeiBarTinyBarConverter INSTANCE = new WeiBarTinyBarConverter();
    public static final Long WEIBARS_TO_TINYBARS = 10_000_000_000L;
    public static final BigInteger WEIBARS_TO_TINYBARS_BIGINT = BigInteger.valueOf(WEIBARS_TO_TINYBARS);

    public byte[] convert(byte[] weibar, boolean signed) {
        if (ArrayUtils.isEmpty(weibar)) {
            return weibar;
        }

        var bigInteger = signed ? new BigInteger(weibar) : new BigInteger(1, weibar);
        return bigInteger.divide(WEIBARS_TO_TINYBARS_BIGINT).toByteArray();
    }

    public Long convert(Long weibar) {
        return weibar == null ? null : weibar / WEIBARS_TO_TINYBARS;
    }
}
