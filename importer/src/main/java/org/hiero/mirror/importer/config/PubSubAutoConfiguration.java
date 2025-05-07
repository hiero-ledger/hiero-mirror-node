// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.autoconfigure.pubsub.GcpPubSubAutoConfiguration;
import com.google.cloud.spring.pubsub.support.converter.JacksonPubSubMessageConverter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.parser.record.pubsub.ConditionalOnPubSubRecordParser;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfigureAfter(GcpPubSubAutoConfiguration.class) // for SubscriberFactory and PublisherFactory
@ConditionalOnPubSubRecordParser
@RequiredArgsConstructor
public class PubSubAutoConfiguration {

    @Bean
    JacksonPubSubMessageConverter jacksonPubSubMessageConverter() {
        return new JacksonPubSubMessageConverter(new ObjectMapper());
    }
}
