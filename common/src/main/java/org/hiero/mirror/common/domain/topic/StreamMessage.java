// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.topic;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({@JsonSubTypes.Type(value = TopicMessage.class, name = "TopicMessage")})
public interface StreamMessage {}
