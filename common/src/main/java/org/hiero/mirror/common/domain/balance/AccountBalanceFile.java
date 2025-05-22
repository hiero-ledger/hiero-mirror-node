// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.balance;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hiero.mirror.common.domain.StreamFile;
import org.hiero.mirror.common.domain.StreamType;

@Builder(toBuilder = true)
@Data
@Entity
@AllArgsConstructor(access = AccessLevel.PRIVATE) // For Builder
@NoArgsConstructor
public class AccountBalanceFile implements StreamFile<AccountBalance> {

    public static final long INVALID_NODE_ID = -1;

    @ToString.Exclude
    private byte[] bytes;

    @Id
    private Long consensusTimestamp;

    private Long count;

    @ToString.Exclude
    private String fileHash;

    @Builder.Default
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Transient
    private List<AccountBalance> items = List.of();

    private Long loadEnd;

    private Long loadStart;

    private String name;

    private Long nodeId;

    private boolean synthetic;

    private int timeOffset;

    @Override
    public StreamFile<AccountBalance> copy() {
        return this.toBuilder().build();
    }

    @Override
    public Long getConsensusStart() {
        return consensusTimestamp;
    }

    @Override
    public void setConsensusStart(Long timestamp) {
        consensusTimestamp = timestamp;
    }

    @Override
    public Long getConsensusEnd() {
        return getConsensusStart();
    }

    @Override
    public StreamType getType() {
        return StreamType.BALANCE;
    }
}
