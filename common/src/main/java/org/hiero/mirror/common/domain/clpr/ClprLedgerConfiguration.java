// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Singleton configuration per CLPR Service. Append-only, same shape as
 * {@link org.hiero.mirror.common.domain.transaction.Prng}.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Data
@NoArgsConstructor
@Table
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

    @Embedded.Nullable
    @JsonUnwrapped
    private ClprThrottle throttle;

    @JsonIgnore
    @Override
    public Long getId() {
        return consensusTimestamp;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid querying before insert
    }
}
