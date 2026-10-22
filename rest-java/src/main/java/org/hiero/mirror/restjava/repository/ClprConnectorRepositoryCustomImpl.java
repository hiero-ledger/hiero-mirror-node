// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.hiero.mirror.restjava.jooq.domain.tables.ClprConnector.CLPR_CONNECTOR;

import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.dto.ClprConnectorsRequest;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.data.domain.Sort.Direction;

@Named
@RequiredArgsConstructor
final class ClprConnectorRepositoryCustomImpl implements ClprConnectorRepositoryCustom {

    private final DSLContext dslContext;

    @Override
    public List<ClprConnector> findAll(final ClprConnectorsRequest request) {
        final var conditions = new ArrayList<Condition>();
        conditions.add(getCondition(CLPR_CONNECTOR.CREATED_TIMESTAMP, RangeOperator.GTE, request.getLowerBound()));
        conditions.add(getCondition(CLPR_CONNECTOR.CREATED_TIMESTAMP, RangeOperator.LTE, request.getUpperBound()));

        final var channelId = request.getChannelId();
        if (channelId != null) {
            conditions.add(CLPR_CONNECTOR.CHANNEL_ID.eq(channelId));
        }

        final var connectorId = request.getConnectorId();
        if (connectorId != null) {
            conditions.add(CLPR_CONNECTOR.CONNECTOR_ID.eq(connectorId));
        }

        final var deleted = request.getDeleted();
        if (deleted != null) {
            // deleted is nullable in the schema, and a connector that was never removed may have it unset
            conditions.add(
                    deleted
                            ? CLPR_CONNECTOR.DELETED.isTrue()
                            : CLPR_CONNECTOR.DELETED.isNull().or(CLPR_CONNECTOR.DELETED.isFalse()));
        }

        final var order = request.getOrder() == Direction.ASC
                ? CLPR_CONNECTOR.CREATED_TIMESTAMP.asc()
                : CLPR_CONNECTOR.CREATED_TIMESTAMP.desc();

        return dslContext
                .selectFrom(CLPR_CONNECTOR)
                .where(conditions)
                .orderBy(order)
                .limit(request.getLimit())
                .fetchInto(ClprConnector.class);
    }
}
