// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.exception;

import java.io.Serial;

public class ExpressionConversionException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 3315905176159519896L;

    public ExpressionConversionException(Throwable e) {
        super(e);
    }
}
