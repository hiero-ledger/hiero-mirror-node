// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.pubsub;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// Exporting to PubSub can be configured using properties in spring.cloud.gcp.pubsub.* and here. See configuration
// docs for more details.
@Data
@Validated
@ConditionalOnPubSubRecordParser
@ConfigurationProperties("hiero.mirror.importer.parser.record.pubsub")
public class PubSubProperties {
    @NotBlank
    private String topicName;

    private int maxSendAttempts = 5;
}
