// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.rest.model.Topic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class TopicMapperTest {

    private CommonMapper commonMapper;
    private CustomFeeMapper customFeeMapper;
    private DomainBuilder domainBuilder;
    private TopicMapper mapper;

    @BeforeEach
    void setup() {
        commonMapper = new CommonMapperImpl();
        final var fixedCustomFeeMapper = new FixedCustomFeeMapperImpl(commonMapper);
        customFeeMapper = new CustomFeeMapperImpl(fixedCustomFeeMapper, commonMapper);
        mapper = new TopicMapperImpl(customFeeMapper, commonMapper);
        domainBuilder = new DomainBuilder();
    }

    @Test
    void map() {
        // given — a deleted topic: the deletion only updates the entity row, so its timestamps are ahead of the topic's
        final var entity =
                domainBuilder.topicEntity().customize(e -> e.deleted(true)).get();
        final var customFee = domainBuilder
                .customFee()
                .customize(c -> c.entityId(entity.getId()))
                .get();
        final var topic = domainBuilder
                .topic()
                .customize(t -> t.createdTimestamp(entity.getCreatedTimestamp() - 2)
                        .id(entity.getId())
                        .timestampRange(Range.atLeast(entity.getTimestampLower() - 1)))
                .get();

        // when, then
        assertThat(mapper.map(customFee, entity, topic))
                .returns(EntityId.of(entity.getAutoRenewAccountId()).toString(), Topic::getAutoRenewAccount)
                .returns(entity.getAutoRenewPeriod(), Topic::getAutoRenewPeriod)
                .returns(commonMapper.mapTimestamp(entity.getCreatedTimestamp()), Topic::getCreatedTimestamp)
                .returns(customFeeMapper.map(customFee), Topic::getCustomFees)
                .returns(true, Topic::getDeleted)
                .returns(entity.getMemo(), Topic::getMemo)
                .returns(commonMapper.mapTimestamp(entity.getTimestampLower()), t -> t.getTimestamp()
                        .getFrom())
                .returns(null, t -> t.getTimestamp().getTo())
                .returns(entity.toEntityId().toString(), Topic::getTopicId);
    }
}
