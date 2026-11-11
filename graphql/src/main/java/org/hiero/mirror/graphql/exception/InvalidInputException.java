// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.graphql.exception;

import java.io.Serial;

/**
 * An input validation failure whose message is safe to return to a GraphQL client.
 */
public final class InvalidInputException extends GraphqlException {

    @Serial
    private static final long serialVersionUID = -6843529518842391845L;

    public InvalidInputException(final String message) {
        super(message);
    }

    public InvalidInputException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
