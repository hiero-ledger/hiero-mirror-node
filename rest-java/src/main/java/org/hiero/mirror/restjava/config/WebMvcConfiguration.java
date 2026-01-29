// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.config;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.restjava.common.RequestParameterArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC configuration to register custom argument resolvers.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfiguration implements WebMvcConfigurer {

    private final RequestParameterArgumentResolver requestParameterArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(requestParameterArgumentResolver);
    }
}
