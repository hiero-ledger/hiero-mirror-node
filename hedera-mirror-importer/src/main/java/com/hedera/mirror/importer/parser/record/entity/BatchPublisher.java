// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.entity;

import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.importer.parser.record.RecordStreamFileListener;
import io.micrometer.core.instrument.Timer;

public interface BatchPublisher extends RecordStreamFileListener {

    Timer.Builder PUBLISH_TIMER = Timer.builder("hedera.mirror.importer.publish.duration")
            .description("The amount of time it took to publish the domain entity")
            .tag("entity", TopicMessage.class.getSimpleName());
}
