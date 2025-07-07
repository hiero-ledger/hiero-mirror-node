// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.config;

import org.hiero.mirror.common.interceptor.GrpcInterceptor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class GrpcTestConfiguration {
    // graphql context does not start if grpcInterceptor isn't separated
    @Bean
    GrpcInterceptor apiTrackingGrpcInterceptor() {
        return new GrpcInterceptor();
    }
}
