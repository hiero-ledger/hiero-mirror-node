// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import io.micrometer.core.instrument.Timer;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.importer.parser.record.RecordStreamFileListener;

public interface BatchPublisher extends RecordStreamFileListener {

    Timer.Builder PUBLISH_TIMER = Timer.builder("hiero.mirror.importer.publish.duration")
            .description("The amount of time it took to publish the domain entity")
            .tag("entity", TopicMessage.class.getSimpleName());
}
