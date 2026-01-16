// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.provider;

import java.io.Serial;
import org.hiero.mirror.importer.exception.ImporterException;

@SuppressWarnings("java:S110")
public class TransientProviderException extends ImporterException {
    @Serial
    private static final long serialVersionUID = -3814433641166281039L;

    public TransientProviderException(Throwable throwable) {
        super(throwable);
    }
}
