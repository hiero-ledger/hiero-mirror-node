// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP;

import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.token.CustomFee;
import org.hiero.mirror.rest.model.Topic;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfiguration.class, uses = CustomFeeMapper.class)
public interface TopicMapper {

    @Mapping(source = "customFee", target = "customFees")
    @Mapping(source = "entity.autoRenewAccountId", target = "autoRenewAccount")
    @Mapping(source = "entity.createdTimestamp", target = "createdTimestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    @Mapping(source = "entity.id", target = "topicId")
    @Mapping(source = "entity.timestampRange", target = "timestamp")
    Topic map(CustomFee customFee, Entity entity, org.hiero.mirror.common.domain.topic.Topic topic);
}
