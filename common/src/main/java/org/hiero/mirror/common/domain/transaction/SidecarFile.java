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
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Data
@Table("sidecar_file") // Explicit table name is recommended
@NoArgsConstructor
public class SidecarFile implements Persistable<SidecarFile.Id> {

    @JsonIgnore
    @ToString.Exclude
    @Transient // Spring Data's version: org.springframework.data.annotation.Transient
    private byte[] actualHash;

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private byte[] bytes;

    @org.springframework.data.annotation.Id
    private long consensusEnd;

    private Integer count;

    // No @Enumerated needed; Spring handles this via standard mapping or custom converters
    private DigestAlgorithm hashAlgorithm;

    @ToString.Exclude
    private byte[] hash;

    @org.springframework.data.annotation.Id
    @Column("id") // Maps the Java field 'index' to the DB column 'id'
    @JsonProperty("id")
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

    @JsonIgnore
    @Override
    public Id getId() {
        return new Id(consensusEnd, index);
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
        private int index;
    }
}
