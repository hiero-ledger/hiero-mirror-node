// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.addressbook;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import java.io.Serial;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

@Builder(toBuilder = true)
@Data
@Entity
@IdClass(AddressBookServiceEndpoint.Id.class)
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE) // For builder
public class AddressBookServiceEndpoint implements Persistable<AddressBookServiceEndpoint.Id> {

    @jakarta.persistence.Id
    private long consensusTimestamp;

    @jakarta.persistence.Id
    @Column(name = "ip_address_v4")
    private String ipAddressV4;

    @jakarta.persistence.Id
    private long nodeId;

    @jakarta.persistence.Id
    private Integer port;

    @jakarta.persistence.Id
    private String domainName;

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
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
    }

    @Data
    public static class Id implements Serializable {

        @Serial
        private static final long serialVersionUID = -7779136597707252814L;

        private long consensusTimestamp;

        @Column(name = "ip_address_v4")
        private String ipAddressV4;

        private long nodeId;

        private Integer port;

        private String domainName;
    }
}
