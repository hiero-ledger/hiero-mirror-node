// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.node;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
import org.springframework.data.relational.core.mapping.Column;

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

    @JsonIgnore
    @Column("service_endpoints")
    private ServiceEndpointsHolder serviceEndpointsColumn;

    private Range<Long> timestampRange;

    @JsonIgnore
    @Column("type")
    private RegisteredNodeTypesHolder typeColumn;

    @JsonSerialize(using = ListToStringSerializer.class)
    public List<Short> getType() {
        return typeColumn == null ? null : typeColumn.types();
    }

    public void setType(List<Short> value) {
        this.typeColumn = RegisteredNodeTypesHolder.of(value);
    }

    @JsonSerialize(using = ObjectToStringSerializer.class)
    public List<RegisteredServiceEndpoint> getServiceEndpoints() {
        return serviceEndpointsColumn == null ? null : serviceEndpointsColumn.items();
    }

    public void setServiceEndpoints(List<RegisteredServiceEndpoint> value) {
        this.serviceEndpointsColumn = ServiceEndpointsHolder.of(value);
    }

    public abstract static class AbstractRegisteredNodeBuilder<
            C extends AbstractRegisteredNode, B extends AbstractRegisteredNodeBuilder<C, B>> {

        public B serviceEndpoints(List<RegisteredServiceEndpoint> endpoints) {
            this.serviceEndpointsColumn = ServiceEndpointsHolder.of(endpoints);
            return self();
        }

        public B type(List<Short> types) {
            this.typeColumn = RegisteredNodeTypesHolder.of(types);
            return self();
        }
    }
}
