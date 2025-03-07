// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.controller;

import com.hedera.mirror.rest.model.Topic;
import com.hedera.mirror.restjava.common.EntityIdNumParameter;
import com.hedera.mirror.restjava.mapper.TopicMapper;
import com.hedera.mirror.restjava.service.CustomFeeService;
import com.hedera.mirror.restjava.service.EntityService;
import com.hedera.mirror.restjava.service.TopicService;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CustomLog
@RequestMapping("/api/v1/topics")
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
