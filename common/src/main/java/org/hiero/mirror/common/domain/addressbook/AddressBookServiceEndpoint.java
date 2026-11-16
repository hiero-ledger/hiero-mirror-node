// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.addressbook;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serial;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.AccessType;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@Builder(toBuilder = true)
@Data
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE) // For builder
@Table
public class AddressBookServiceEndpoint implements Persistable<AddressBookServiceEndpoint.Id> {

    @Transient
    private long consensusTimestamp;

    @Transient
    private String ipAddressV4;

    @Transient
    private long nodeId;

    @Transient
    private Integer port;

    @Transient
    private String domainName;

    @AccessType(AccessType.Type.PROPERTY)
    @Embedded.Nullable
    @org.springframework.data.annotation.Id
    @JsonIgnore
    @Override
    public Id getId() {
        Id id = new Id();
        id.setConsensusTimestamp(consensusTimestamp);
        id.setDomainName(domainName);
        id.setIpAddressV4(ipAddressV4);
        id.setNodeId(nodeId);
        id.setPort(port);
        return id;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid querying before insert
    }

    public void setId(Id id) {
        if (id == null) {
            return;
        }

        this.consensusTimestamp = id.getConsensusTimestamp();
        this.ipAddressV4 = id.getIpAddressV4();
        this.nodeId = id.getNodeId();
        this.port = id.getPort();
        this.domainName = id.getDomainName();
    }

    @AllArgsConstructor
    @Data
    @NoArgsConstructor
    public static class Id implements Serializable {

        @Serial
        private static final long serialVersionUID = -7779136597707252814L;

        private long consensusTimestamp;

        private String ipAddressV4;

        private long nodeId;

        private Integer port;

        private String domainName;
    }
}
