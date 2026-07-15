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
import lombok.With;
import org.hiero.mirror.common.converter.ListToStringSerializer;
import org.hiero.mirror.common.domain.DigestAlgorithm;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.InsertOnlyProperty;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Data
@Table("sidecar_file")
@NoArgsConstructor
public class SidecarFile implements Persistable<SidecarFile.Id> {

    @org.springframework.data.annotation.Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    private Id id;

    @JsonIgnore
    @ToString.Exclude
    @Transient
    private byte[] actualHash;

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private byte[] bytes;

    private Integer count;

    private DigestAlgorithm hashAlgorithm;

    @ToString.Exclude
    private byte[] hash;

    private String name;

    @Builder.Default
    @JsonIgnore
    @ToString.Exclude
    @Transient // Critical: This field uses Protobuf types that break AOT if not transient
    private List<TransactionSidecarRecord> records = Collections.emptyList();

    private Integer size;

    @Builder.Default
    @JsonSerialize(using = ListToStringSerializer.class)
    private List<Integer> types = Collections.emptyList();

    public static class SidecarFileBuilder {
        public SidecarFileBuilder consensusEnd(long consensusEnd) {
            this.id = (this.id == null ? new Id() : this.id).withConsensusEnd(consensusEnd);
            return this;
        }

        public SidecarFileBuilder index(int index) {
            this.id = (this.id == null ? new Id() : this.id).withIndex(index);
            return this;
        }
    }

    public long getConsensusEnd() {
        return id != null ? id.getConsensusEnd() : 0L;
    }

    // Exposed to Jackson as "id" so batch COPY serialization matches the db column
    @JsonProperty("id")
    public int getIndex() {
        return id != null ? id.getIndex() : 0;
    }

    public void setConsensusEnd(long consensusEnd) {
        id = (id == null ? new Id() : id).withConsensusEnd(consensusEnd);
    }

    @JsonProperty("id")
    public void setIndex(int index) {
        id = (id == null ? new Id() : id).withIndex(index);
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
    @With
    public static class Id implements Serializable {
        @Serial
        private static final long serialVersionUID = -5844173241500874821L;

        @InsertOnlyProperty
        private long consensusEnd;

        @Column("id")
        @JsonProperty("id")
        @InsertOnlyProperty
        private int index;
    }
}
