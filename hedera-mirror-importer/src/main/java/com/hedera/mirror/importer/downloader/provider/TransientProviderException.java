// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.provider;

import com.hedera.mirror.importer.exception.ImporterException;
import java.io.Serial;

@SuppressWarnings("java:S110")
public class TransientProviderException extends ImporterException {
    @Serial
    private static final long serialVersionUID = -3814433641166281039L;

    public TransientProviderException(Throwable throwable) {
        super(throwable);
    }
}
