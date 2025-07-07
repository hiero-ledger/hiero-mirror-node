// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.interceptor;

import static org.hiero.mirror.common.util.Constants.CURRENT_ENDPOINT;

import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.parser.Parser;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import reactor.core.publisher.Mono;

public final class GraphQLInterceptor implements WebGraphQlInterceptor {

    private final Parser parser = new Parser();

    @Override
    public Mono<WebGraphQlResponse> intercept(final WebGraphQlRequest request, final Chain chain) {
        final var endpoint = extractEndpointFromQuery(request);
        request.configureExecutionInput((input, builder) -> builder.graphQLContext(
                        contextBuilder -> contextBuilder.of(CURRENT_ENDPOINT, endpoint))
                .build());
        return chain.next(request);
    }

    private String extractEndpointFromQuery(final WebGraphQlRequest request) {
        final var document = parser.parseDocument(request.getDocument());
        final var operations = document.getDefinitionsOfType(OperationDefinition.class);
        for (final var operation : operations) {
            if (operation.getOperation() == OperationDefinition.Operation.QUERY) {
                final var selectionSet = operation.getSelectionSet();

                final var fields = selectionSet.getSelectionsOfType(Field.class);

                return fields.stream().map(Field::getName).toList().getFirst();
            }
        }
        return null;
    }
}
