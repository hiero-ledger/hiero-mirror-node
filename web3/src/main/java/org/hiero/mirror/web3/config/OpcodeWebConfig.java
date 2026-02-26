// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.controller.AcceptEncodingInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnProperty(prefix = "hiero.mirror.web3.opcode.tracer", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class OpcodeWebConfig implements WebMvcConfigurer {

    private final AcceptEncodingInterceptor acceptEncodingInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(acceptEncodingInterceptor).addPathPatterns("/api/v1/contracts/results/*/opcodes");
    }
}
