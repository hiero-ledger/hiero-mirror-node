// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.addressbook;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.LinkedHashSet;
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
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

/**
 * The address_book_entry table has composite primary key (consensus_timestamp, node_id), but only
 * consensusTimestamp is mapped as the Spring Data JDBC @Id. An embedded composite id cannot be used
 * here since entity is both a child of the AddressBook aggregate and the parent of serviceEndpoints,
 * and a composite id would make Spring Data JDBC expect composite back-reference columns
 * (address_book_entry_consensus_timestamp/address_book_entry_node_id) on
 * address_book_service_endpoint, which do not exist.
 *
 * An @Id is required since AddressBookEntry is exposed as a standalone aggregate root through
 * CrudRepository<AddressBookEntry, Id> (which mandates exactly one id), and as the owner of the
 * {@link #serviceEndpoints} {@code @MappedCollection} it must supply the identifier value that Spring Data JDBC stamps
 * into each endpoint's back-reference column. That column is consensus_timestamp. Annotating nodeId with @Id instead
 * would write the node_id into the endpoints' consensus_timestamp column.
 *
 * Because that @Id is not unique on its own (all entries in one book share a timestamp),
 * AddressBookEntryRepository#findById is overridden to filter on both key
 * columns, and isNew() always returns true to force inserts and skip the id-based existence check.
 * nodeId is therefore persisted as a plain column while still participating in the entry's logical identity
 * through equals/hashCode.
 *
 * serviceEndpoints is a Set rather than a List on purpose. A List @MappedCollection requires a keyColumn, which
 * Spring Data JDBC treats as the list index and writes on save. Pointing that keyColumn at node_id was a latent
 * trap: it did not actually corrupt node_id only because AddressBookServiceEndpoint independently maps node_id
 * through its own embedded id, whose value took precedence over the index write. A Set needs no keyColumn, so it
 * is correct by construction and matches the endpoint table's primary key
 * (consensus_timestamp, node_id, ip_address_v4, port), under which endpoints are already unique. Since
 * @MappedCollection supports only a single back-reference column, endpoints still load scoped to
 * consensus_timestamp alone; AddressBookEntryEndpointCallback drops the cross-node endpoints after conversion
 * to restore per-node_id scoping.
 */
@Builder(toBuilder = true)
@Data
@Table
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE) // For builder
public class AddressBookEntry implements Persistable<AddressBookEntry.Id> {

    private String description;

    // Only the first column of the composite (consensus_timestamp, node_id) primary key
    @org.springframework.data.annotation.Id
    private long consensusTimestamp;

    private String memo;

    private EntityId nodeAccountId;

    @ToString.Exclude
    private byte[] nodeCertHash;

    private long nodeId;

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
    private Set<AddressBookServiceEndpoint> serviceEndpoints = new LinkedHashSet<>();

    private Long stake;

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
        return new Id(consensusTimestamp, nodeId);
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid querying before insert
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Id implements Serializable {
        private static final long serialVersionUID = -3761184325551298389L;
        private long consensusTimestamp;
        private long nodeId;
    }
}
