// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.domain.Persistable;

/**
 * Singleton configuration per CLPR Service. Append-only, same shape as
 * {@link org.hiero.mirror.common.domain.transaction.Prng}.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Data
@Entity
@NoArgsConstructor
public class ClprLedgerConfiguration implements Persistable<Long> {

    private String chainId;

    @Id
    private long consensusTimestamp;

    @ToString.Exclude
    private byte[] initialTrustAnchor;

    @ToString.Exclude
    private byte[] initialTrustAnchorId;

    private long protocolVersion;

    @ToString.Exclude
    private byte[] serviceAddress;

    @Embedded
    private ClprThrottle throttle;

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
