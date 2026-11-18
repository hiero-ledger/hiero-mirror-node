// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.hiero.mirror.common.converter.ObjectToStringSerializer.OBJECT_MAPPER;
import static org.hiero.mirror.restjava.jooq.domain.Tables.REGISTERED_NODE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.inject.Named;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint;
import org.hiero.mirror.restjava.converter.LongRangeConverter;
import org.hiero.mirror.restjava.jooq.domain.tables.records.RegisteredNodeRecord;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort.Direction;

@Named
@RequiredArgsConstructor
class RegisteredNodeRepositoryCustomImpl implements RegisteredNodeRepositoryCustom {

    private static final TypeReference<List<RegisteredServiceEndpoint>> SERVICE_ENDPOINTS_TYPE =
            new TypeReference<>() {};

    private final DSLContext dslContext;

    @Override
    public List<RegisteredNode> findRegisteredNodes(
            long lowerBound, long upperBound, @Nullable Short type, Direction order, int limit) {
        var condition = REGISTERED_NODE
                .REGISTERED_NODE_ID
                .between(lowerBound, upperBound)
                .and(REGISTERED_NODE.DELETED.isFalse());

        if (type != null) {
            condition = condition.and(REGISTERED_NODE.TYPE.contains(new Short[] {type}));
        }

        final var id = REGISTERED_NODE.REGISTERED_NODE_ID;
        return dslContext
                .selectFrom(REGISTERED_NODE)
                .where(condition)
                .orderBy(order.isAscending() ? id.asc() : id.desc())
                .limit(limit)
                .fetch(RegisteredNodeRepositoryCustomImpl::toRegisteredNode);
    }

    private static RegisteredNode toRegisteredNode(RegisteredNodeRecord nodeRecord) {
        return RegisteredNode.builder()
                .adminKey(nodeRecord.getAdminKey())
                .createdTimestamp(nodeRecord.getCreatedTimestamp())
                .deleted(nodeRecord.getDeleted())
                .description(nodeRecord.getDescription())
                .registeredNodeId(nodeRecord.getRegisteredNodeId())
                .serviceEndpoints(toServiceEndpoints(nodeRecord.getServiceEndpoints()))
                .timestampRange(LongRangeConverter.INSTANCE.convert(nodeRecord.getTimestampRange()))
                .type(Arrays.asList(nodeRecord.getType()))
                .build();
    }

    private static @Nullable List<RegisteredServiceEndpoint> toServiceEndpoints(@Nullable JSONB serviceEndpoints) {
        if (serviceEndpoints == null) {
            return null;
        }

        try {
            return OBJECT_MAPPER.readValue(serviceEndpoints.data(), SERVICE_ENDPOINTS_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to parse registered node service endpoints", e);
        }
    }
}
