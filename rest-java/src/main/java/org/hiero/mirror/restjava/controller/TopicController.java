// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import static org.hiero.mirror.restjava.common.Constants.APPLICATION_JSON;

import com.google.common.collect.Range;
import java.util.List;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.token.CustomFee;
import org.hiero.mirror.rest.model.Topic;
import org.hiero.mirror.restjava.mapper.TopicMapper;
import org.hiero.mirror.restjava.parameter.EntityIdNumParameter;
import org.hiero.mirror.restjava.service.CustomFeeService;
import org.hiero.mirror.restjava.service.EntityService;
import org.hiero.mirror.restjava.service.TopicService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CustomLog
@RequestMapping(value = "/api/v1/topics", produces = APPLICATION_JSON)
@RequiredArgsConstructor
@RestController
public final class TopicController {

    private final CustomFeeService customFeeService;
    private final EntityService entityService;
    private final TopicMapper topicMapper;
    private final TopicService topicService;

    @GetMapping(value = "/{id}")
    Topic getTopic(@PathVariable EntityIdNumParameter id) {
        var topic = topicService.findById(id.id());
        var entity = entityService.findById(id.id());
        var customFee = customFeeService
                .findByIdOptional(id.id())
                .orElseGet(() -> emptyCustomFeePlaceholder(id.id().getId()));
        return topicMapper.map(customFee, entity, topic);
    }

    private static CustomFee emptyCustomFeePlaceholder(long entityId) {
        return CustomFee.builder()
                .entityId(entityId)
                .fixedFees(List.of())
                .fractionalFees(List.of())
                .royaltyFees(List.of())
                .timestampRange(Range.atLeast(0L))
                .build();
    }
}
