// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.config;

import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.fees.StandaloneFeeCalculator;
import com.hedera.node.app.fees.StandaloneFeeCalculatorImpl;
import com.hedera.node.app.service.entityid.impl.AppEntityIdFactory;
import com.hedera.node.app.workflows.standalone.TransactionExecutors;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.restjava.jooq.DomainRecordMapperProvider;
import org.hiero.mirror.restjava.service.fee.FeeEstimationState;
import org.hiero.mirror.restjava.service.fee.FeeProperties;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.jooq.autoconfigure.DefaultConfigurationCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

@Configuration
@EnableCaching
@RequiredArgsConstructor
class RestJavaConfiguration {

    private static final Map<String, String> INTRINSIC_PROPERTIES = Map.of("fees.simpleFeesEnabled", "true");

    private final FormattingConversionService mvcConversionService;

    @PostConstruct
    void initialize() {
        // Register application converters to use case-insensitive string to enum converter.
        ApplicationConversionService.addApplicationConverters(mvcConversionService);
    }

    @Bean
    StandaloneFeeCalculator standaloneFeeCalculator(
            CommonProperties commonProperties, FeeEstimationState feeEstimationState) {
        final var overrideValues = new HashMap<>(INTRINSIC_PROPERTIES);
        overrideValues.put("hedera.realm", String.valueOf(commonProperties.getRealm()));
        overrideValues.put("hedera.shard", String.valueOf(commonProperties.getShard()));
        final var properties = TransactionExecutors.Properties.newBuilder()
                .state(feeEstimationState)
                .appProperties(overrideValues)
                .build();
        final var config = new ConfigProviderImpl(false, null, overrideValues).getConfiguration();
        return new StandaloneFeeCalculatorImpl(feeEstimationState, properties, new AppEntityIdFactory(config));
    }

    @Bean
    CacheManager feeCacheManager(FeeProperties feeProperties) {
        final var cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(Set.of("simpleFeeSchedule"));
        cacheManager.setCacheSpecification(feeProperties.getCacheSpec());
        return cacheManager;
    }

    @Bean
    DefaultConfigurationCustomizer configurationCustomizer(DomainRecordMapperProvider domainRecordMapperProvider) {
        return c -> c.set(domainRecordMapperProvider).settings().withRenderSchema(false);
    }

    @Bean
    FilterRegistrationBean<ShallowEtagHeaderFilter> etagFilter() {
        final var filterRegistrationBean = new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        filterRegistrationBean.addUrlPatterns("/api/*");
        return filterRegistrationBean;
    }

    @Bean
    ProtobufHttpMessageConverter protobufHttpMessageConverter() {
        final var protobufMediaType = new MediaType("application", "protobuf");
        final var extensionRegistry = ExtensionRegistry.newInstance();

        final var converter = new ProtobufHttpMessageConverter() {
            @Override
            protected Message readInternal(Class<? extends Message> clazz, HttpInputMessage inputMessage)
                    throws IOException, HttpMessageNotReadableException {
                final var message = super.readInternal(clazz, inputMessage);
                final var contentType = inputMessage.getHeaders().getContentType();

                if (protobufMediaType.isCompatibleWith(contentType)) {
                    return message.toBuilder()
                            .mergeFrom(inputMessage.getBody(), extensionRegistry)
                            .build();
                }

                return message;
            }
        };

        final var mediaTypes = new ArrayList<>(converter.getSupportedMediaTypes());
        mediaTypes.add(protobufMediaType);
        converter.setSupportedMediaTypes(mediaTypes);
        return converter;
    }
}
