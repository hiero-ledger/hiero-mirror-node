// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.exception;

import java.io.Serial;

@SuppressWarnings("java:S110")
public class FieldInaccessibleException extends ImporterException {
    @Serial
    private static final long serialVersionUID = 5190034850686608814L;

    public FieldInaccessibleException(Throwable throwable) {
        super(throwable);
    }
}
