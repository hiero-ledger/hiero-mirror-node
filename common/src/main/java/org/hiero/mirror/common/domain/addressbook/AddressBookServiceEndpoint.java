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
import lombok.With;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.InsertOnlyProperty;
import org.springframework.data.relational.core.mapping.Table;

@Builder(toBuilder = true)
@Data
@Table("address_book_service_endpoint")
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
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
        return true;
    }

    public void setConsensusTimestamp(long consensusTimestamp) {
        id = (id == null ? new Id() : id).withConsensusTimestamp(consensusTimestamp);
    }

    public void setIpAddressV4(String ipAddressV4) {
        id = (id == null ? new Id() : id).withIpAddressV4(ipAddressV4);
    }

    public void setNodeId(long nodeId) {
        id = (id == null ? new Id() : id).withNodeId(nodeId);
    }

    public void setPort(Integer port) {
        id = (id == null ? new Id() : id).withPort(port);
    }

    public void setDomainName(String domainName) {
        id = (id == null ? new Id() : id).withDomainName(domainName);
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

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @With
    public static class Id implements Serializable {

        @Serial
        private static final long serialVersionUID = -7779136597707252814L;

        @InsertOnlyProperty
        private long consensusTimestamp;

        @Column("ip_address_v4")
        @InsertOnlyProperty
        private String ipAddressV4;

        @InsertOnlyProperty
        private long nodeId;

        @InsertOnlyProperty
        private Integer port;

        @InsertOnlyProperty
        private String domainName;
    }

    public static class AddressBookServiceEndpointBuilder {
        public AddressBookServiceEndpointBuilder consensusTimestamp(long consensusTimestamp) {
            this.id = (this.id == null ? new Id() : this.id).withConsensusTimestamp(consensusTimestamp);
            return this;
        }

        public AddressBookServiceEndpointBuilder ipAddressV4(String ipAddressV4) {
            this.id = (this.id == null ? new Id() : this.id).withIpAddressV4(ipAddressV4);
            return this;
        }

        public AddressBookServiceEndpointBuilder nodeId(long nodeId) {
            this.id = (this.id == null ? new Id() : this.id).withNodeId(nodeId);
            return this;
        }

        public AddressBookServiceEndpointBuilder port(Integer port) {
            this.id = (this.id == null ? new Id() : this.id).withPort(port);
            return this;
        }

        public AddressBookServiceEndpointBuilder domainName(String domainName) {
            this.id = (this.id == null ? new Id() : this.id).withDomainName(domainName);
            return this;
        }
    }
}
