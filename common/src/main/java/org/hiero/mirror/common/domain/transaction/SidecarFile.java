// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hiero.mirror.common.converter.ListToStringSerializer;
import org.hiero.mirror.common.domain.DigestAlgorithm;
import org.springframework.data.annotation.AccessType;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Data
@NoArgsConstructor
@Table
public class SidecarFile implements Persistable<SidecarFile.Id> {

    @JsonIgnore
    @ToString.Exclude
    @Transient
    private byte[] actualHash;

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private byte[] bytes;

    @Transient
    private long consensusEnd;

    private Integer count;

    private DigestAlgorithm hashAlgorithm;

    @ToString.Exclude
    private byte[] hash;

    @JsonProperty("id")
    @Transient
    private int index;

    private String name;

    @Builder.Default
    @JsonIgnore
    @ToString.Exclude
    @Transient
    private List<TransactionSidecarRecord> records = Collections.emptyList();

    private Integer size;

    @Builder.Default
    @JsonSerialize(using = ListToStringSerializer.class)
    private List<Integer> types = Collections.emptyList();

    @AccessType(AccessType.Type.PROPERTY)
    @Embedded.Nullable
    @org.springframework.data.annotation.Id
    @JsonIgnore
    @Override
    public Id getId() {
        var id = new Id();
        id.setConsensusEnd(consensusEnd);
        id.setIndex(index);
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

        this.consensusEnd = id.getConsensusEnd();
        this.index = id.getIndex();
    }

    @AllArgsConstructor
    @Data
    @NoArgsConstructor
    public static class Id implements Serializable {

        @Serial
        private static final long serialVersionUID = -5844173241500874821L;

        private long consensusEnd;

        @Column("id")
        private int index;
    }
}
