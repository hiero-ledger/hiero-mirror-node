// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hiero.mirror.common.converter.ObjectToStringSerializer;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** The authoritative, on-ledger endpoint set for this CLPR Service. Append-only; no history pair needed. */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Data
@NoArgsConstructor
@Table
public class ClprEndpointManifest implements Persistable<Long> {

    @Id
    private long consensusTimestamp;

    @Column("endpoints")
    @JsonIgnore
    private ClprEndpointListHolder endpointsColumn;

    @ToString.Exclude
    private byte[] serviceAddress;

    private long version;

    @JsonSerialize(using = ObjectToStringSerializer.class)
    public List<ClprEndpoint> getEndpoints() {
        return endpointsColumn == null ? null : endpointsColumn.items();
    }

    @JsonIgnore
    @Override
    public Long getId() {
        return consensusTimestamp;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid querying before insert
    }

    public void setEndpoints(List<ClprEndpoint> endpoints) {
        this.endpointsColumn = ClprEndpointListHolder.of(endpoints);
    }

    public static class ClprEndpointManifestBuilder {

        public ClprEndpointManifestBuilder endpoints(List<ClprEndpoint> endpoints) {
            this.endpointsColumn = ClprEndpointListHolder.of(endpoints);
            return this;
        }
    }
}
