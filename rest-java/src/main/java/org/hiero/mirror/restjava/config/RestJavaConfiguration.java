// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.CaffeineSpec;
import com.github.benmanes.caffeine.cache.LoadingCache;
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
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.restjava.jooq.DomainRecordMapperProvider;
import org.hiero.mirror.restjava.service.FeeEstimationState;
import org.hiero.mirror.restjava.service.FileService;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.jooq.autoconfigure.DefaultConfigurationCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

@Configuration
@RequiredArgsConstructor
class RestJavaConfiguration {

    private final FormattingConversionService mvcConversionService;

    @PostConstruct
    void initialize() {
        // Register application converters to use case-insensitive string to enum converter.
        ApplicationConversionService.addApplicationConverters(mvcConversionService);
    }

    @Bean
    LoadingCache<String, StandaloneFeeCalculator> intrinsicFeeCalculatorCache(
            FeeProperties feeProperties, FileService fileService, SystemEntity systemEntity) {
        return Caffeine.from(CaffeineSpec.parse(feeProperties.getCacheSpec())).build(k -> {
            final var bytes = fileService.getSimpleFeeScheduleBytes();
            final var state = new FeeEstimationState(bytes, systemEntity);
            final var intrinsicProperties = feeProperties.getIntrinsicProperties();
            final var properties = TransactionExecutors.Properties.newBuilder()
                    .state(state)
                    .appProperties(intrinsicProperties)
                    .build();
            final var config = new ConfigProviderImpl(false, null, intrinsicProperties).getConfiguration();
            return new StandaloneFeeCalculatorImpl(state, properties, new AppEntityIdFactory(config));
        });
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
