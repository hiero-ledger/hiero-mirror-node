// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.node;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Range;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hiero.mirror.common.converter.ListToStringSerializer;
import org.hiero.mirror.common.converter.ObjectToStringSerializer;
import org.hiero.mirror.common.domain.History;
import org.hiero.mirror.common.domain.UpsertColumn;
import org.hiero.mirror.common.domain.Upsertable;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.data.annotation.Id;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public abstract class AbstractNode implements History {

    // Handled by global EntityId converters
    private EntityId accountId;

    @ToString.Exclude
    private byte[] adminKey;

    private Long createdTimestamp;

    private Boolean declineReward;

    private boolean deleted;

    @JsonSerialize(using = ListToStringSerializer.class)
    private List<Long> associatedRegisteredNodes;

    // JDBC: Requires custom Reading/Writing converters for JSON/JSONB
    @JsonSerialize(using = ObjectToStringSerializer.class)
    @UpsertColumn(coalesce = "case when ({0} -> ''port'')::integer = -1 then null else coalesce({0}, e_{0}) end")
    private ServiceEndpoint grpcProxyEndpoint;

    @Id
    private Long nodeId;

    // JDBC: Requires custom Reading/Writing converters for PG 'int8range'
    private Range<Long> timestampRange;
}
