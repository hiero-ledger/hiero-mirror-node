// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.graphql.exception;

/**
 * An input validation failure whose message is safe to return to a GraphQL client.
 * Messages must be application-controlled and must not include internal exception messages.
 */
public final class InvalidInputException extends RuntimeException {

    public InvalidInputException(String message) {
        super(message);
    }

    public InvalidInputException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
