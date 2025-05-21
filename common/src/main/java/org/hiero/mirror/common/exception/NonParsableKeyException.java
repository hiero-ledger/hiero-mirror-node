// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.exception;

import java.io.Serial;

public class NonParsableKeyException extends MirrorNodeException {

    @Serial
    private static final long serialVersionUID = 8082824937229796277L;

    public NonParsableKeyException(Throwable throwable) {
        super(throwable);
    }
}
