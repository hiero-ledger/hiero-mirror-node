// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.graphql.config;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import jakarta.inject.Named;
import org.hiero.mirror.graphql.exception.GraphqlException;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;

/**
 * Returns the message only for {@link GraphqlException}, whose messages are safe to show clients. Anything else stays
 * unresolved, so Spring GraphQL logs it server-side and returns a generic INTERNAL_ERROR with the execution ID.
 */
@Named
public class CustomExceptionResolver extends DataFetcherExceptionResolverAdapter {
    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        if (!(ex instanceof GraphqlException)) {
            return null;
        }

        return GraphqlErrorBuilder.newError()
                .errorType(ErrorType.ValidationError)
                .message(ex.getMessage())
                .path(env.getExecutionStepInfo().getPath())
                .location(env.getField().getSourceLocation())
                .build();
    }
}
