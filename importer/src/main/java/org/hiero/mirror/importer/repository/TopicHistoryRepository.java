// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.topic.TopicHistory;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public interface TopicHistoryRepository extends CrudRepository<TopicHistory, Long>, RetentionRepository {

    @Modifying
    @Override
    @Query(value = "delete from topic_history where timestamp_range << int8range(?1, null)")
    int prune(long consensusTimestamp);
}
