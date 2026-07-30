// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.exception;

import java.io.Serial;

@SuppressWarnings("java:S110")
public class NoBlockNodeAvailableException extends ImporterException {

    @Serial
    private static final long serialVersionUID = 4829104719572910473L;

    public NoBlockNodeAvailableException(final long blockNumber) {
        super("No block node can provide block " + blockNumber);
    }
}
