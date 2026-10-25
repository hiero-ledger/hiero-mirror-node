// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.exception;

import org.hiero.mirror.common.exception.MirrorNodeException;

public class SubscriptionTimeoutException extends MirrorNodeException {

    private static final long serialVersionUID = 7284519033641827451L;

    public SubscriptionTimeoutException(String message) {
        super(message);
    }
}
