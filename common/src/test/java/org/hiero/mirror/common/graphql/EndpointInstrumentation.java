// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.graphql;

import static org.hiero.mirror.common.util.Constants.CURRENT_ENDPOINT;
import static org.hiero.mirror.common.util.Constants.UNKNOWN_ENDPOINT;

import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.hiero.mirror.common.util.EndpointContext;

public final class EndpointInstrumentation implements Instrumentation {

    /**
     * GraphQL-Java Instrumentation that ensures the CURRENT_ENDPOINT value
     * (published to the GraphQLContext by our WebGraphQlInterceptor)
     * is available on every DataFetcher thread.
     *
     * <p>Because GraphQL execution may hop threads for field resolution,
     * we must explicitly read from GraphQLContext and re-populate our
     * ThreadLocal before each resolver runs—and then clear it immediately
     * afterward to avoid leakage.</p>
     */
    @Override
    public DataFetcher<?> instrumentDataFetcher(
            final DataFetcher<?> dataFetcher,
            final InstrumentationFieldFetchParameters parameters,
            final InstrumentationState state) {

        // if it's a plain property fetcher, skip wrapping
        if (parameters.isTrivialDataFetcher()) {
            return dataFetcher;
        }

        return (DataFetchingEnvironment env) -> {
            // read the endpoint straight from GraphQLContext (it survives thread hops)
            final var endpoint = env.getGraphQlContext().getOrDefault(CURRENT_ENDPOINT, UNKNOWN_ENDPOINT);

            // setting the ThreadLocal so RepositoryUsageInterceptor can see it
            EndpointContext.setCurrentEndpoint(endpoint);
            try {
                return dataFetcher.get(env);
            } finally {
                // clear immediately to avoid leaking into other fetchers
                EndpointContext.clearCurrentEndpoint();
            }
        };
    }
}
