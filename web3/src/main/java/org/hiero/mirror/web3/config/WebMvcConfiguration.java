// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.common.TransactionIdOrHashParameter;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
final class WebMvcConfiguration implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, TransactionIdOrHashParameter.class, TransactionIdOrHashParameter::valueOf);
    }
}
