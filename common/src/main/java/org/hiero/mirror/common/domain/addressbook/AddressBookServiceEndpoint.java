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
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Builder(toBuilder = true)
@Data
@Table("address_book_service_endpoint")
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AddressBookServiceEndpoint implements Persistable<AddressBookServiceEndpoint.Id> {

    @org.springframework.data.annotation.Id
    private long consensusTimestamp;

    @org.springframework.data.annotation.Id
    @Column("ip_address_v4")
    private String ipAddressV4;

    @org.springframework.data.annotation.Id
    private long nodeId;

    @org.springframework.data.annotation.Id
    private Integer port;

    @org.springframework.data.annotation.Id
    private String domainName;

    @JsonIgnore
    @Override
    public Id getId() {
        return new Id(consensusTimestamp, ipAddressV4, nodeId, port, domainName);
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        // Natural IDs for service endpoints should always trigger an INSERT
        return true;
    }

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
