// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.topic.TopicMessageLookup;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface TopicMessageLookupRepository
        extends CrudRepository<TopicMessageLookup, TopicMessageLookup.Id>, RetentionRepository {
    @Modifying
    @Override
    @Query("delete from topic_message_lookup where upper(timestamp_range) <= :consensusTimestamp")
    int prune(long consensusTimestamp);
}
