// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.spring.pubsub.PubSubAdmin;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.pubsub.v1.PubsubMessage;
import com.hedera.mirror.importer.PubSubIntegrationTest.Configuration;
import com.hedera.mirror.importer.parser.record.pubsub.PubSubProperties;
import jakarta.annotation.Resource;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@Import(Configuration.class)
@SpringBootTest(
        properties = {
            "spring.cloud.gcp.core.enabled=true",
            "spring.cloud.gcp.pubsub.enabled=true",
            "spring.profiles.include=pubsub",
            "hedera.mirror.importer.parser.record.entity.enabled=false",
            "hedera.mirror.importer.parser.record.pubsub.enabled=true"
        })
public abstract class PubSubIntegrationTest extends ImporterIntegrationTest {

    private static final String SUBSCRIPTION = "testSubscription";

    @Resource
    private PubSubProperties properties;

    @Resource
    private PubSubTemplate pubSubTemplate;

    @Resource
    private PubSubAdmin pubSubAdmin;

    @BeforeEach
    void setup() {
        String topicName = properties.getTopicName();
        // delete old topic and subscription if present
        try {
            pubSubAdmin.deleteTopic(topicName);
            pubSubAdmin.deleteSubscription(SUBSCRIPTION);
        } catch (NotFoundException e) {
            // ignored
        }
        pubSubAdmin.createTopic(topicName);
        pubSubAdmin.createSubscription(SUBSCRIPTION, topicName);
    }

    // Synchronously waits for numMessages from the subscription. Acks them and extracts payloads from them.
    protected List<PubsubMessage> getAllMessages(int numMessages) {
        return pubSubTemplate.pull(SUBSCRIPTION, numMessages, false).stream()
                .map(m -> {
                    m.ack();
                    return m.getPubsubMessage();
                })
                .toList();
    }

    @TestConfiguration
    static class Configuration {
        // Avoid the warning stacktrace in the logs about no default credentials
        @Bean
        CredentialsProvider credentialsProvider() {
            return new NoCredentialsProvider();
        }
    }
}
