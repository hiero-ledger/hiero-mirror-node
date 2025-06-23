// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.exception;

import java.io.Serial;

@SuppressWarnings("java:S110")
public class BlockStreamException extends ImporterException {

    @Serial
    private static final long serialVersionUID = -7695889012245051457L;

    public BlockStreamException(String message) {
        super(message);
    }

    public BlockStreamException(Throwable cause) {
        super(cause);
    }
}
