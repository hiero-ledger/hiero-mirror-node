// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hiero.mirror.common.converter.ObjectToStringSerializer;
import org.springframework.data.domain.Persistable;

/**
 * The authoritative, on-ledger endpoint set for this CLPR Service. Every update is a complete, self-sufficient
 * value keyed by its own {@code consensusTimestamp}, so the latest row is simply the one with the highest
 * timestamp -- append-only, no history pair needed.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Data
@Entity
@NoArgsConstructor
public class ClprEndpointManifest implements Persistable<Long> {

    @Id
    private long consensusTimestamp;

    @JsonSerialize(using = ObjectToStringSerializer.class)
    @JdbcTypeCode(SqlTypes.JSON)
    private List<ClprEndpoint> endpoints;

    @ToString.Exclude
    private byte[] serviceAddress;

    private long version;

    @JsonIgnore
    @Override
    public Long getId() {
        return consensusTimestamp;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
    }
}
