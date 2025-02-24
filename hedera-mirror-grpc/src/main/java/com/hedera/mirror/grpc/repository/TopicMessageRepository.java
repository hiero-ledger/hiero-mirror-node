// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.grpc.repository;

import com.hedera.mirror.common.domain.topic.TopicMessage;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface TopicMessageRepository extends CrudRepository<TopicMessage, Long>, TopicMessageRepositoryCustom {

    @Query(
            value =
                    """
       with related_transaction as (
         select entity_id as topic_id, consensus_timestamp
         from transaction
         where consensus_timestamp > ?1 and result = 22 and type = 27
         order by consensus_timestamp
         limit ?2
       )
       select t.*
       from topic_message as t
       join related_transaction using (topic_id, consensus_timestamp)
       order by t.consensus_timestamp
       """,
            nativeQuery = true)
    List<TopicMessage> findLatest(long consensusTimestamp, int limit);
}
