// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.graphql.exception;

import java.io.Serial;
import org.hiero.mirror.common.exception.MirrorNodeException;

/**
 * Base type for GraphQL exceptions whose message is returned to the client. Messages must be application-controlled
 * and must not include internal exception messages. It is sealed so every exposed message type is reviewed here.
 */
public abstract sealed class GraphqlException extends MirrorNodeException
        permits InvalidInputException, NotImplementedException {

    @Serial
    private static final long serialVersionUID = 3216546412339587413L;

    protected GraphqlException(final String message) {
        super(message);
    }

    protected GraphqlException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
