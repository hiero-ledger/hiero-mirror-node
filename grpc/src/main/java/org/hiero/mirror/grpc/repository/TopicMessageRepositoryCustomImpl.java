// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.repository;

import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.stream.Stream;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hibernate.jpa.HibernateHints;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.grpc.domain.TopicMessageFilter;

@CustomLog
@Named
@RequiredArgsConstructor
public class TopicMessageRepositoryCustomImpl implements TopicMessageRepositoryCustom {

    private static final String CONSENSUS_TIMESTAMP = "consensusTimestamp";
    private static final String TOPIC_ID = "topicId";
    // make the cost estimation of using the index on (topic_id, consensus_timestamp) lower than that of
    // the primary key so pg planner will choose the better index when querying topic messages by id
    private static final String TOPIC_MESSAGES_BY_ID_QUERY_HINT = "set local random_page_cost = 0";

    private final EntityManager entityManager;

    @Override
    public Stream<TopicMessage> findByFilter(TopicMessageFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<TopicMessage> query = cb.createQuery(TopicMessage.class);
        Root<TopicMessage> root = query.from(TopicMessage.class);

        Predicate predicate = cb.and(
                cb.equal(root.get(TOPIC_ID), filter.getTopicId()),
                cb.greaterThanOrEqualTo(root.get(CONSENSUS_TIMESTAMP), filter.getStartTime()));

        if (filter.getEndTime() != null) {
            predicate = cb.and(predicate, cb.lessThan(root.get(CONSENSUS_TIMESTAMP), filter.getEndTime()));
        }

        query = query.select(root).where(predicate).orderBy(cb.asc(root.get(CONSENSUS_TIMESTAMP)));

        TypedQuery<TopicMessage> typedQuery = entityManager.createQuery(query);
        typedQuery.setHint(HibernateHints.HINT_READ_ONLY, true);

        if (filter.hasLimit()) {
            typedQuery.setMaxResults((int) filter.getLimit());
        }

        if (filter.getLimit() != 1) {
            // only apply the hint when limit is not 1
            entityManager.createNativeQuery(TOPIC_MESSAGES_BY_ID_QUERY_HINT).executeUpdate();
        }

        return typedQuery.getResultList().stream(); // getResultStream()'s cursor doesn't work with reactive streams
    }
}
