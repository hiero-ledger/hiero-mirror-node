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
 * Registers {@link OpcodesConcurrencyInterceptor} for the opcodes endpoint only. Uses {@link ObjectProvider} so
 * unrelated {@code @WebMvcTest} slices don't need an {@link OpcodesProperties} bean.
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
final class OpcodesWebMvcConfiguration implements WebMvcConfigurer {

    private final ObjectProvider<OpcodesProperties> propertiesProvider;

    @Override
    public void addInterceptors(@NonNull final InterceptorRegistry registry) {
        final var properties = propertiesProvider.getIfAvailable(OpcodesProperties::new);
        final var traceMemoryBudget = new Semaphore(properties.getMaxConcurrentTraceBytes());
        registry.addInterceptor(
                        new OpcodesConcurrencyInterceptor(traceMemoryBudget, TraceWeightEstimator.of(properties)))
                .addPathPatterns(OPCODES_URI);
    }
}
