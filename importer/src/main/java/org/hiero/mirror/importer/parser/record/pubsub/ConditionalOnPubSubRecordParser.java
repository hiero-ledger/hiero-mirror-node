// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.pubsub;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ConditionalOnProperty(name = "spring.cloud.gcp.pubsub.enabled", havingValue = "true")
public @interface ConditionalOnPubSubRecordParser {}
