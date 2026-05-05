// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.addressbook;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.commons.codec.binary.Hex;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.exception.NonParsableKeyException;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

@Builder(toBuilder = true)
@Data
@Table("address_book_entry")
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AddressBookEntry implements Persistable<AddressBookEntry.Id> {

    private String description;

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    private Id id;

    private String memo;

    private EntityId nodeAccountId;

    @ToString.Exclude
    private byte[] nodeCertHash;

    @ToString.Exclude
    private String publicKey;

    @EqualsAndHashCode.Exclude
    @Getter(lazy = true)
    @ToString.Exclude
    @Transient
    private final PublicKey publicKeyObject = parsePublicKey();

    @Builder.Default
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    @MappedCollection(idColumn = "consensus_timestamp")
    private Set<AddressBookServiceEndpoint> serviceEndpoints = new HashSet<>();

    private Long stake;

    public long getConsensusTimestamp() {
        return id.getConsensusTimestamp();
    }

    public long getNodeId() {
        return id.getNodeId();
    }

    private PublicKey parsePublicKey() {
        try {
            byte[] bytes = Hex.decodeHex(publicKey);
            EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(bytes);
            var keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(publicKeySpec);
        } catch (Exception e) {
            throw new NonParsableKeyException(e);
        }
    }

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
        private static final long serialVersionUID = -3761184325551298389L;
        private long consensusTimestamp;
        private long nodeId;
    }

    public static class AddressBookEntryBuilder {
        public AddressBookEntryBuilder consensusTimestamp(long consensusTimestamp) {
            if (this.id == null) this.id = new Id();
            this.id.setConsensusTimestamp(consensusTimestamp);
            return this;
        }

        public AddressBookEntryBuilder nodeId(long nodeId) {
            if (this.id == null) this.id = new Id();
            this.id.setNodeId(nodeId);
            return this;
        }
    }
}
