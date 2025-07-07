// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.config;

import org.hiero.mirror.common.graphql.EndpointInstrumentation;
import org.hiero.mirror.common.interceptor.GraphQLInterceptor;
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer;
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
