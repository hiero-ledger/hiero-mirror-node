// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.exception;

import org.hiero.mirror.common.exception.MirrorNodeException;

public class SubscriptionLimitException extends MirrorNodeException {

    private static final long serialVersionUID = 4812635901742031847L;

    public SubscriptionLimitException(String message) {
        super(message);
    }
}
