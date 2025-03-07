// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

import com.hedera.mirror.common.domain.topic.TopicMessage;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface TopicMessageRepository extends CrudRepository<TopicMessage, Long>, RetentionRepository {

    @Modifying
    @Override
    @Query("delete from TopicMessage where consensusTimestamp <= ?1")
    int prune(long consensusTimestamp);
}
