// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.utils;

import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.parameter.TimestampParameter;
import org.hiero.mirror.restjava.service.Bound;
import org.jooq.Field;

public final class RangeHelper {
    private RangeHelper() {}

    public static Bound timestampBound(TimestampParameter[] timestamp, String parameterName, Field<Long> field) {
        if (timestamp == null || timestamp.length == 0) {
            return Bound.EMPTY;
        }

        for (int i = 0; i < timestamp.length; ++i) {
            final var param = timestamp[i];
            if (param.operator() == RangeOperator.EQ) {
                timestamp[i] = new TimestampParameter(RangeOperator.LTE, param.value());
            }
        }

        return new Bound(timestamp, false, parameterName, field);
    }
}
