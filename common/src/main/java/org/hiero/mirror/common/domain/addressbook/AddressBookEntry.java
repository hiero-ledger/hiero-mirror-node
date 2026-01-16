// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.addressbook;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Transient;
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
import org.springframework.data.domain.Persistable;

@Builder(toBuilder = true)
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE) // For builder
public class AddressBookEntry implements Persistable<AddressBookEntry.Id> {

    private String description;

    @EmbeddedId
    @JsonUnwrapped
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
    @JoinColumn(name = "consensusTimestamp", referencedColumnName = "consensusTimestamp")
    @JoinColumn(name = "nodeId", referencedColumnName = "nodeId")
    @JsonIgnore
    @OneToMany(
            cascade = {CascadeType.ALL},
            orphanRemoval = true,
            fetch = FetchType.EAGER)
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
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
    }

    // We have to use @EmbeddedId due to a Hibernate bug, but to avoid changing code we still support flattened IDs.
    public static class AddressBookEntryBuilder {
        public AddressBookEntryBuilder consensusTimestamp(long consensusTimestamp) {
            getId().setConsensusTimestamp(consensusTimestamp);
            return this;
        }

        public AddressBookEntryBuilder nodeId(long nodeId) {
            getId().setNodeId(nodeId);
            return this;
        }

        private Id getId() {
            if (id == null) {
                id = new Id();
            }
            return id;
        }
    }

    @Data
    @Embeddable
    public static class Id implements Serializable {

        private static final long serialVersionUID = -3761184325551298389L;

        private long consensusTimestamp;
        private long nodeId;
    }
}
