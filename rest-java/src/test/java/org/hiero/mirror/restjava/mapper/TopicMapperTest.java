// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.hederahashgraph.api.proto.java.FeeExemptKeyList;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Key.KeyCase;
import com.hederahashgraph.api.proto.java.KeyList;
import java.util.Collections;
import org.apache.commons.codec.binary.Hex;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.rest.model.ConsensusCustomFees;
import org.hiero.mirror.rest.model.Key.TypeEnum;
import org.hiero.mirror.rest.model.Topic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TopicMapperTest {

    private CustomFeeMapper customFeeMapper;
    private CommonMapper commonMapper;
    private DomainBuilder domainBuilder;
    private TopicMapper mapper;

    @BeforeEach
    void setup() {
        commonMapper = new CommonMapperImpl();
        var fixedCustomFeeMapper = new FixedCustomFeeMapperImpl(commonMapper);
        customFeeMapper = new CustomFeeMapperImpl(fixedCustomFeeMapper, commonMapper);
        mapper = new TopicMapperImpl(customFeeMapper, commonMapper);
        domainBuilder = new DomainBuilder();
    }

    @Test
    void map() {
        var key = domainBuilder.key(KeyCase.ED25519);
        var entity = domainBuilder.topicEntity().get();
        var customFee = domainBuilder
                .customFee()
                .customize(c -> c.entityId(entity.getId()))
                .get();
        var topic = domainBuilder
                .topic()
                .customize(t -> t.adminKey(key)
                        .createdTimestamp(entity.getCreatedTimestamp())
                        .id(entity.getId())
                        .submitKey(key)
                        .timestampRange(entity.getTimestampRange()))
                .get();

        assertThat(mapper.map(customFee, entity, topic))
                .returns(TypeEnum.ED25519, t -> t.getAdminKey().getType())
                .returns(
                        Hex.encodeHexString(topic.getAdminKey()),
                        t -> "1220" + t.getAdminKey().getKey())
                .returns(EntityId.of(entity.getAutoRenewAccountId()).toString(), Topic::getAutoRenewAccount)
                .returns(entity.getAutoRenewPeriod(), Topic::getAutoRenewPeriod)
                .returns(commonMapper.mapTimestamp(topic.getCreatedTimestamp()), Topic::getCreatedTimestamp)
                .returns(customFeeMapper.map(customFee), Topic::getCustomFees)
                .returns(entity.getDeleted(), Topic::getDeleted)
                .returns(entity.getMemo(), Topic::getMemo)
                .returns(TypeEnum.ED25519, t -> t.getSubmitKey().getType())
                .returns(
                        Hex.encodeHexString(topic.getSubmitKey()),
                        t -> "1220" + t.getSubmitKey().getKey())
                .returns(commonMapper.mapTimestamp(topic.getTimestampLower()), t -> t.getTimestamp()
                        .getFrom())
                .returns(null, t -> t.getTimestamp().getTo())
                .returns(entity.toEntityId().toString(), Topic::getTopicId);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            true, true
            false, false
            """)
    void mapEmptyAndNulls(boolean nullFixedFees, boolean keysCleared) {
        var topicEntity = new Entity();
        var customFee = domainBuilder
                .customFee()
                .customize(
                        c -> c.entityId(topicEntity.getId()).fixedFees(nullFixedFees ? null : Collections.emptyList()))
                .get();
        var topic = org.hiero.mirror.common.domain.topic.Topic.builder()
                .id(topicEntity.getId())
                .feeExemptKeyList(
                        keysCleared ? FeeExemptKeyList.getDefaultInstance().toByteArray() : null)
                .feeScheduleKey(
                        keysCleared
                                ? Key.newBuilder()
                                        .setKeyList(KeyList.getDefaultInstance())
                                        .build()
                                        .toByteArray()
                                : null)
                .build();
        var expectedCustomFees = new ConsensusCustomFees()
                .createdTimestamp(commonMapper.mapLowerRange(customFee.getTimestampRange()))
                .fixedFees(Collections.emptyList());
        assertThat(mapper.map(customFee, topicEntity, topic))
                .returns(null, Topic::getAdminKey)
                .returns(null, Topic::getAutoRenewAccount)
                .returns(null, Topic::getAutoRenewPeriod)
                .returns(expectedCustomFees, Topic::getCustomFees)
                .returns(null, Topic::getCreatedTimestamp)
                .returns(null, Topic::getDeleted)
                .returns(null, Topic::getMemo)
                .returns(null, Topic::getSubmitKey)
                .returns(null, Topic::getTimestamp)
                .returns(null, Topic::getTopicId);
    }
}
