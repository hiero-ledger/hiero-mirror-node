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
import org.hiero.mirror.common.domain.UpsertColumn;
import org.hiero.mirror.common.domain.Upsertable;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public abstract class AbstractNode implements History {

    private EntityId accountId;

    @ToString.Exclude
    private byte[] adminKey;

    private Long createdTimestamp;

    private Boolean declineReward;

    private boolean deleted;

    @JsonIgnore
    @Column("associated_registered_nodes")
    private AssociatedRegisteredNodeIds associatedRegisteredNodesColumn;

    @JsonSerialize(using = ObjectToStringSerializer.class)
    @UpsertColumn(coalesce = "case when ({0} -> ''port'')::integer = -1 then null else coalesce({0}, e_{0}) end")
    private ServiceEndpoint grpcProxyEndpoint;

    @Id
    private Long nodeId;

    private Range<Long> timestampRange;

    @JsonSerialize(using = ListToStringSerializer.class)
    public List<Long> getAssociatedRegisteredNodes() {
        return associatedRegisteredNodesColumn == null ? null : associatedRegisteredNodesColumn.ids();
    }

    public void setAssociatedRegisteredNodes(List<Long> value) {
        this.associatedRegisteredNodesColumn = AssociatedRegisteredNodeIds.of(value);
    }

    public abstract static class AbstractNodeBuilder<C extends AbstractNode, B extends AbstractNodeBuilder<C, B>> {

        public B associatedRegisteredNodes(List<Long> ids) {
            this.associatedRegisteredNodesColumn = AssociatedRegisteredNodeIds.of(ids);
            return self();
        }
    }
}
