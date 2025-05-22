// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.IdClass;
import jakarta.persistence.Transient;
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
import org.springframework.data.domain.Persistable;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Data
@Entity
@IdClass(SidecarFile.Id.class)
@NoArgsConstructor
public class SidecarFile implements Persistable<SidecarFile.Id> {

    @JsonIgnore
    @ToString.Exclude
    @Transient
    private byte[] actualHash;

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private byte[] bytes;

    @jakarta.persistence.Id
    private long consensusEnd;

    private Integer count;

    @Enumerated
    private DigestAlgorithm hashAlgorithm;

    @ToString.Exclude
    private byte[] hash;

    @Column(name = "id")
    @JsonProperty("id")
    @jakarta.persistence.Id
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
        var id = new Id();
        id.setConsensusEnd(consensusEnd);
        id.setIndex(index);
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
        private static final long serialVersionUID = -5844173241500874821L;

        private long consensusEnd;

        private int index;
    }
}
