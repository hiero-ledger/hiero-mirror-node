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
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Embedded;
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

    /**
     * Custom builder to maintain compatibility with existing code.
     * Note: We map 'index' in Java to the 'id' column in the DB within the Id class.
     */
    public static class SidecarFileBuilder {
        public SidecarFileBuilder consensusEnd(long consensusEnd) {
            initId();
            this.id.setConsensusEnd(consensusEnd);
            return this;
        }

        public SidecarFileBuilder index(int index) {
            initId();
            this.id.setIndex(index);
            return this;
        }

        private void initId() {
            if (this.id == null) {
                this.id = new Id();
            }
        }
    }

    // Convenience accessors
    public long getConsensusEnd() {
        return id != null ? id.getConsensusEnd() : 0L;
    }

    public int getIndex() {
        return id != null ? id.getIndex() : 0;
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
        @Serial
        private static final long serialVersionUID = -5844173241500874821L;

        private long consensusEnd;

        @Column("id") // Maintains mapping: Java 'index' -> DB 'id'
        @JsonProperty("id")
        private int index;
    }
}
