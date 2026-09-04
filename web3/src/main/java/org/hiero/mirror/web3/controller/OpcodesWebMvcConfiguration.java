// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.controller;

import static org.hiero.mirror.web3.utils.Constants.OPCODES_URI;

import java.util.concurrent.Semaphore;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@link OpcodesConcurrencyInterceptor} for the opcodes endpoint only. Uses {@link ObjectProvider} rather
 * than {@link OpcodesProperties} directly so unrelated {@code @WebMvcTest} slices - which auto-detect every
 * {@link WebMvcConfigurer} bean - don't fail to start without one.
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
final class OpcodesWebMvcConfiguration implements WebMvcConfigurer {

    private final ObjectProvider<OpcodesProperties> propertiesProvider;

    @Override
    public void addInterceptors(@NonNull final InterceptorRegistry registry) {
        final var maxConcurrentTraces =
                propertiesProvider.getIfAvailable(OpcodesProperties::new).getMaxConcurrentTraces();
        final var concurrentTraceLimiter = new Semaphore(maxConcurrentTraces);
        registry.addInterceptor(new OpcodesConcurrencyInterceptor(concurrentTraceLimiter))
                .addPathPatterns(OPCODES_URI);
    }
}
