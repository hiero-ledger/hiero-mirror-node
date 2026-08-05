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
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@Builder
@Data
@Table
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE) // For builder
public class AddressBookServiceEndpoint implements Persistable<AddressBookServiceEndpoint.Id> {

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    private Id id;

    @JsonIgnore
    @Override
    public Id getId() {
        return id;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid Spring Data JDBC querying before insert
    }

    public void setConsensusTimestamp(long consensusTimestamp) {
        id().setConsensusTimestamp(consensusTimestamp);
    }

    public void setIpAddressV4(String ipAddressV4) {
        id().setIpAddressV4(ipAddressV4);
    }

    public void setNodeId(long nodeId) {
        id().setNodeId(nodeId);
    }

    public void setPort(Integer port) {
        id().setPort(port);
    }

    public void setDomainName(String domainName) {
        id().setDomainName(domainName);
    }

    private Id id() {
        if (id == null) {
            id = new Id();
        }

        return id;
    }

    public long getConsensusTimestamp() {
        return id != null ? id.getConsensusTimestamp() : 0L;
    }

    public String getIpAddressV4() {
        return id != null ? id.getIpAddressV4() : null;
    }

    public long getNodeId() {
        return id != null ? id.getNodeId() : 0L;
    }

    public Integer getPort() {
        return id != null ? id.getPort() : null;
    }

    public String getDomainName() {
        return id != null ? id.getDomainName() : null;
    }

    @Builder
    @Data
    @AllArgsConstructor
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
