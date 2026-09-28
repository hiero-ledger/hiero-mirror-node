// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.common.TransactionIdOrHashParameter;
import org.hiero.mirror.web3.throttle.RequestThrottleInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
final class WebMvcConfiguration implements WebMvcConfigurer {

    private final RequestThrottleInterceptor requestThrottleInterceptor;

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, TransactionIdOrHashParameter.class, TransactionIdOrHashParameter::valueOf);
    }

    @Override
    public void addInterceptors(final InterceptorRegistry registry) {
        registry.addInterceptor(requestThrottleInterceptor);
    }
}
