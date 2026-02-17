// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import static org.hiero.mirror.restjava.common.Constants.APPLICATION_JSON;

import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
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
public class TopicController {

    private final CustomFeeService customFeeService;
    private final EntityService entityService;
    private final TopicMapper topicMapper;
    private final TopicService topicService;

    @GetMapping(value = "/{id}")
    Topic getTopic(@PathVariable EntityIdNumParameter id) {
        var topic = topicService.findById(id.id());
        var entity = entityService.findById(id.id());
        var customFee = customFeeService.findById(id.id());
        return topicMapper.map(customFee, entity, topic);
    }
}
