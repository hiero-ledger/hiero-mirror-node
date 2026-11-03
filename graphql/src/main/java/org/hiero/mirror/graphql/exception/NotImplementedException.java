// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.graphql.exception;

import java.io.Serial;

/**
 * A request for functionality the server does not support yet. Its message is safe to return to a GraphQL client.
 */
public final class NotImplementedException extends GraphqlException {

    @Serial
    private static final long serialVersionUID = -2203837562912364937L;

    public NotImplementedException(final String message) {
        super(message);
    }
}
