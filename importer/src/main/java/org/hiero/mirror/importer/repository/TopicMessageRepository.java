// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface TopicMessageRepository extends CrudRepository<TopicMessage, Long>, RetentionRepository {

    @Modifying
    @Override
    @Query("delete from topic_message where consensus_timestamp <= :consensusTimestamp")
    int prune(@Param("consensusTimestamp") long consensusTimestamp);
}
