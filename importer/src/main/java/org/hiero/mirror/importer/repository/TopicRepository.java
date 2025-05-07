// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import com.hedera.mirror.common.domain.topic.Topic;
import org.springframework.data.repository.CrudRepository;

public interface TopicRepository extends CrudRepository<Topic, Long> {}
