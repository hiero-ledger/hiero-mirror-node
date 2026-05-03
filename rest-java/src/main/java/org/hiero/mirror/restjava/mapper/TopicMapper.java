// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.token.CustomFee;
import org.hiero.mirror.common.domain.topic.Topic;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfiguration.class, uses = CustomFeeMapper.class)
public interface TopicMapper {

    @Mapping(target = "topicId", source = "topic.id")
    @Mapping(target = "timestamp", source = "topic.timestampRange")
    @Mapping(
            target = "createdTimestamp",
            source = "topic.createdTimestamp",
            qualifiedByName = CommonMapper.QUALIFIER_TIMESTAMP)
    @Mapping(target = "adminKey", source = "topic.adminKey")
    @Mapping(target = "submitKey", source = "topic.submitKey")
    @Mapping(target = "feeScheduleKey", source = "topic.feeScheduleKey")
    @Mapping(target = "feeExemptKeyList", source = "topic.feeExemptKeyList")
    @Mapping(target = "memo", source = "entity.memo", defaultValue = "")
    @Mapping(target = "deleted", source = "entity.deleted")
    @Mapping(target = "autoRenewAccount", source = "entity.autoRenewAccountId")
    @Mapping(target = "autoRenewPeriod", source = "entity.autoRenewPeriod")
    @Mapping(target = "customFees", source = "customFee")
    org.hiero.mirror.rest.model.Topic map(CustomFee customFee, Entity entity, Topic topic);
}
