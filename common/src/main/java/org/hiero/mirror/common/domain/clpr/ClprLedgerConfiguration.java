// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
 * Singleton configuration per CLPR Service. Every update is a complete, self-sufficient value keyed by its own
 * {@code consensusTimestamp}, so the latest row is simply the one with the highest timestamp -- append-only, no
 * history pair needed, the same shape as {@link org.hiero.mirror.common.domain.transaction.Prng}.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Data
@Entity
@NoArgsConstructor
public class ClprLedgerConfiguration implements Persistable<Long> {

    @Id
    private long consensusTimestamp;

    private String chainId;

    @ToString.Exclude
    private byte[] initialTrustAnchor;

    @ToString.Exclude
    private byte[] initialTrustAnchorId;

    private Long maxGasPerMessage;

    private Long maxLocalEndpoints;

    private Long maxMessagePayloadBytes;

    private Long maxMessagesPerBundle;

    private Long maxPeerEndpoints;

    private Long maxQueueDepth;

    private Long maxSyncBytes;

    private long protocolVersion;

    @ToString.Exclude
    private byte[] serviceAddress;

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
