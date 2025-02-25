// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.mapper;

import static com.hedera.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.token.CustomFee;
import com.hedera.mirror.rest.model.Topic;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfiguration.class, uses = CustomFeeMapper.class)
public interface TopicMapper {

    @Mapping(source = "customFee", target = "customFees")
    @Mapping(source = "entity.autoRenewAccountId", target = "autoRenewAccount")
    @Mapping(source = "entity.createdTimestamp", target = "createdTimestamp", qualifiedByName = QUALIFIER_TIMESTAMP)
    @Mapping(source = "entity.id", target = "topicId")
    @Mapping(source = "entity.timestampRange", target = "timestamp")
    Topic map(CustomFee customFee, Entity entity, com.hedera.mirror.common.domain.topic.Topic topic);
}
