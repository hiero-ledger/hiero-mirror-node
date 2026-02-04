// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.graphql.config;

import org.hiero.mirror.graphql.interceptor.EndpointInstrumentation;
import org.hiero.mirror.graphql.interceptor.GraphQLInterceptor;
import org.springframework.boot.graphql.autoconfigure.GraphQlSourceBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class GraphqlTestConfiguration {

    @Bean
    GraphQlSourceBuilderCustomizer instrumentationCustomizer(final EndpointInstrumentation instrumentation) {
        return builder -> builder.configureGraphQl(graphQl -> graphQl.instrumentation(instrumentation));
    }

    @Bean
    GraphQLInterceptor graphQLInterceptor() {
        return new GraphQLInterceptor();
    }

    @Bean
    EndpointInstrumentation endpointInstrumentation() {
        return new EndpointInstrumentation();
    }
}
