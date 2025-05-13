// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.spec.builder;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import jakarta.inject.Named;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.hiero.mirror.restjava.spec.model.SpecSetup;

@Named
class TopicMessageBuilder extends AbstractEntityBuilder<TopicMessage, TopicMessage.TopicMessageBuilder> {

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return specSetup::topicMessages;
    }

    @Override
    protected TopicMessage.TopicMessageBuilder getEntityBuilder(SpecBuilderContext builderContext) {
        return TopicMessage.builder()
                .message("message".getBytes(StandardCharsets.UTF_8))
                .payerAccountId(EntityId.of(3L))
                .runningHash("running_hash".getBytes(StandardCharsets.UTF_8))
                .runningHashVersion(2);
    }

    @Override
    protected TopicMessage getFinalEntity(TopicMessage.TopicMessageBuilder builder, Map<String, Object> account) {
        return builder.build();
    }
}
