// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.exception;

import java.io.Serial;

@SuppressWarnings("java:S110")
public class InvalidConfigurationException extends ImporterException {

    @Serial
    private static final long serialVersionUID = -2996303169427541497L;

    public InvalidConfigurationException(String message) {
        super(message);
    }

    public InvalidConfigurationException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
