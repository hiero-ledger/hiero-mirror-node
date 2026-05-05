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
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Embedded;
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

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Id implements Serializable {

        @Serial
        private static final long serialVersionUID = -7779136597707252814L;

        private long consensusTimestamp;

        @Column("ip_address_v4")
        private String ipAddressV4;

        private long nodeId;

        private Integer port;

        private String domainName;
    }

    public static class AddressBookServiceEndpointBuilder {
        public AddressBookServiceEndpointBuilder consensusTimestamp(long consensusTimestamp) {
            if (this.id == null) this.id = new Id();
            this.id.setConsensusTimestamp(consensusTimestamp);
            return this;
        }

        public AddressBookServiceEndpointBuilder ipAddressV4(String ipAddressV4) {
            if (this.id == null) this.id = new Id();
            this.id.setIpAddressV4(ipAddressV4);
            return this;
        }

        public AddressBookServiceEndpointBuilder nodeId(long nodeId) {
            if (this.id == null) this.id = new Id();
            this.id.setNodeId(nodeId);
            return this;
        }

        public AddressBookServiceEndpointBuilder port(Integer port) {
            if (this.id == null) this.id = new Id();
            this.id.setPort(port);
            return this;
        }

        public AddressBookServiceEndpointBuilder domainName(String domainName) {
            if (this.id == null) this.id = new Id();
            this.id.setDomainName(domainName);
            return this;
        }
    }
}
