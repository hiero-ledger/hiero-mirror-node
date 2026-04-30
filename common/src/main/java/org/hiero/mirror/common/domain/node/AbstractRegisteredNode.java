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
import org.hiero.mirror.common.domain.Upsertable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public abstract class AbstractRegisteredNode implements History {

    @ToString.Exclude
    private byte[] adminKey;

    private Long createdTimestamp;

    private boolean deleted;

    private String description;

    @Id
    private Long registeredNodeId;

    // JDBC: Requires custom Reading/Writing converters for JSON/JSONB
    @JsonSerialize(using = ObjectToStringSerializer.class)
    private List<RegisteredServiceEndpoint> serviceEndpoints;

    // JDBC: Requires custom Reading/Writing converters for PG 'int8range'
    @Transient
    private Range<Long> timestampRange;

    // JDBC: Requires custom Reading/Writing converters if stored as CSV or Array
    @JsonSerialize(using = ListToStringSerializer.class)
    private List<Short> type;
}
