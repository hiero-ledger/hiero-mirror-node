// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.config;

import org.hiero.mirror.grpc.interceptor.GrpcInterceptor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class GrpcTestConfiguration {
    @Bean
    GrpcInterceptor apiTrackingGrpcInterceptor() {
        return new GrpcInterceptor();
    }
}
